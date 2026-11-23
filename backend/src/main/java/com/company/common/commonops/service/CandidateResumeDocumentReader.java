package com.company.common.commonops.service;

import com.company.common.support.service.CodeFailure;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class CandidateResumeDocumentReader {
    /** 업로드 한도(spring.servlet.multipart.max-file-size 10000000B)와 같은 값. */
    public static final int MAX_BYTES = 10_000_000;
    private static final int MAX_TEXT = 200_000;
    private static final int MAX_EXPANDED = 32 * 1024 * 1024;
    /** PDF를 읽을 때 쓰는 메모리 상한(임시 파일 없이 메모리만)과 읽는 쪽 수. */
    private static final long PDF_MEMORY = 64L * 1024 * 1024;
    private static final int PDF_PAGES = 30;
    /** 시안 받는 파일 목록 중 서버가 글자를 꺼낼 수 있는 것(rtf는 한글 코드 페이지를 읽지 못해 받지 않음). */
    private static final Set<String> EXTENSIONS = Set.of("txt", "pdf", "doc", "docx", "ppt", "pptx", "hwp", "hwpx");
    public static final String TYPE_MESSAGE =
        "Word·PDF·PPT·한글(HWP)·TXT 파일만 올릴 수 있습니다. 스캔한 그림 파일은 글자를 꺼낼 수 없어 직접 적어 주세요.";
    public static final String SIZE_MESSAGE = "10MB 이하 파일만 올릴 수 있습니다.";

    public String read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid();
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        String name = safeName(file.getOriginalFilename());
        String ext = name.lastIndexOf('.') < 0 ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        // 브라우저가 보내는 형식 값은 믿을 수 없어(한글은 빈 값·여러 이름) 확장자로 고르고, 그림 형식만 막는다.
        String mime = Objects.requireNonNullElse(file.getContentType(), "");
        mime = mime.contains(";") ? mime.substring(0, mime.indexOf(';')) : mime;
        mime = mime.strip().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(ext) || mime.startsWith("image/")) throw unsupported();
        try (var stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw tooLarge();
            if (bytes.length == 0) throw invalid();
            String text = switch (ext) {
                case "txt" -> plain(bytes);
                case "pdf" -> pdf(bytes);
                case "docx", "pptx", "hwpx" -> officeXml(bytes, ext);
                case "doc" -> word(bytes);
                case "ppt" -> slides(bytes);
                case "hwp" -> hwp(bytes);
                default -> throw unsupported();
            };
            if (text.isBlank() || text.length() > MAX_TEXT) throw invalid();
            return text;
        } catch (CodeFailure failure) {
            throw failure;
        } catch (Exception | OutOfMemoryError | StackOverflowError failure) {
            // Never include parser exception messages: they can contain document text or filenames.
            throw invalid();
        }
    }

    public static String safeName(String name) {
        if (name == null) return "";
        String clean = name.replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        return clean.length() > 200 ? clean.substring(clean.length() - 200) : clean;
    }

    private static String plain(byte[] bytes) throws CharacterCodingException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        if (text.chars().anyMatch(c -> c < 32 && c != '\n' && c != '\r' && c != '\t')) throw invalid();
        return text.replace("\uFEFF", "").strip();
    }

    private static String pdf(byte[] bytes) throws IOException {
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw invalid();
        // 메모리만 쓰고(임시 파일 없음) 상한을 두며, 앞 30쪽까지만 읽는다. 상한을 넘거나 깨진 파일은 read()에서 400으로 바꾼다.
        try (var document = Loader.loadPDF(bytes, "", null, null, MemoryUsageSetting.setupMainMemoryOnly(PDF_MEMORY).streamCache)) {
            if (document.isEncrypted()) throw invalid();
            var output = new LimitedWriter();
            var stripper = new PDFTextStripper();
            stripper.setEndPage(PDF_PAGES);
            stripper.writeText(document, output);
            return output.toString();
        }
    }

    private static String word(byte[] bytes) throws IOException {
        try (var document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getRange().text();
        }
    }

    private static String slides(byte[] bytes) throws IOException {
        var text = new LimitedWriter();
        try (var document = new HSLFSlideShow(new ByteArrayInputStream(bytes))) {
            for (var slide : document.getSlides()) for (var paragraph : slide.getTextParagraphs()) {
                for (var run : paragraph) for (var part : run.getTextRuns()) text.append(part.getRawText());
                text.append('\n');
            }
        }
        return text.toString();
    }

    private static String officeXml(byte[] bytes, String ext) throws Exception {
        var text = new LimitedWriter();
        boolean main = false;
        boolean types = false;
        int expanded = 0;
        Set<String> entries = new HashSet<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entries.size() >= 2000 || !entries.add(entry.getName())) throw invalid();
                byte[] data = zip.readNBytes(MAX_EXPANDED - expanded + 1);
                expanded += data.length;
                if (expanded > MAX_EXPANDED) throw invalid();
                String path = entry.getName();
                if (ext.equals("hwpx")) {
                    // 한글 hwpx: 맨 앞 mimetype 항목이 application/hwp+zip, 본문은 Contents/section0.xml…
                    if (path.equals("mimetype")) types = new String(data, StandardCharsets.US_ASCII).strip().equals("application/hwp+zip");
                } else if (path.equals("[Content_Types].xml")) {
                    String mainType = ext.equals("docx")
                        ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                        : "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
                    types = parseXml(data, null).contains(mainType);
                }
                if (path.endsWith(".rels")) parseXml(data, null);
                boolean content = switch (ext) {
                    case "docx" -> path.equals("word/document.xml");
                    case "hwpx" -> path.matches("Contents/section[0-9]+\\.xml");
                    default -> path.matches("ppt/slides/slide[0-9]+\\.xml");
                };
                if (content) { parseXml(data, text); main = true; }
            }
        }
        if (!main || !types) throw invalid();
        return text.toString();
    }

    private static Set<String> parseXml(byte[] bytes, LimitedWriter output) throws Exception {
        Set<String> contentTypes = new HashSet<>();
        var factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var parser = factory.newSAXParser();
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        parser.parse(new ByteArrayInputStream(bytes), new DefaultHandler() {
            private boolean inText;
            /** 표 칸(tc) 안의 깊이. 칸 안의 문단 끝은 줄을 바꾸지 않아, 같은 줄의 칸이 「성명→탭→홍길동」 한 줄로 남는다. */
            private int cell;
            @Override public void startElement(String uri, String local, String q, Attributes attributes) {
                if ("External".equals(attributes.getValue("TargetMode"))) throw invalid();
                if (attributes.getValue("ContentType") != null) contentTypes.add(attributes.getValue("ContentType"));
                if ("tc".equals(local)) cell++;
                inText = "t".equals(local);
            }
            @Override public void characters(char[] chars, int start, int length) throws org.xml.sax.SAXException {
                if (output != null && inText) {
                    try { output.write(chars, start, length); } catch (IOException e) { throw new org.xml.sax.SAXException("Text limit"); }
                }
            }
            @Override public void endElement(String uri, String local, String q) throws org.xml.sax.SAXException {
                if ("t".equals(local)) inText = false;
                if (output == null) return;
                try {
                    if ("tc".equals(local)) { cell = Math.max(0, cell - 1); output.append('\t'); }
                    else if ("tr".equals(local)) output.append('\n');
                    else if (Set.of("p", "br").contains(local)) output.append(cell > 0 ? ' ' : '\n');
                } catch (IOException e) { throw new org.xml.sax.SAXException("Text limit"); }
            }
        });
        return contentTypes;
    }

    private static String hwp(byte[] bytes) throws IOException {
        try (var filesystem = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
            var root = filesystem.getRoot();
            byte[] header = document(root, "FileHeader");
            if (header.length < 40 || !new String(header, 0, 17, StandardCharsets.US_ASCII).equals("HWP Document File")) throw invalid();
            int flags = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(36);
            if ((flags & (2 | 4 | 256)) != 0 || header[35] != 5) throw invalid();
            var body = (DirectoryEntry) root.getEntry("BodyText");
            var output = new LimitedWriter();
            int expanded = 0;
            for (int section = 0; body.hasEntry("Section" + section); section++) {
                byte[] data = document(body, "Section" + section);
                if ((flags & 1) != 0) {
                    var inflater = new Inflater(true);
                    try (var stream = new InflaterInputStream(new ByteArrayInputStream(data), inflater)) {
                        data = stream.readNBytes(MAX_EXPANDED - expanded + 1);
                    } finally { inflater.end(); }
                }
                expanded += data.length;
                if (expanded > MAX_EXPANDED) throw invalid();
                var records = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                while (records.hasRemaining()) {
                    if (records.remaining() < 4) throw invalid();
                    int record = records.getInt();
                    int tag = record & 0x3ff;
                    int size = record >>> 20;
                    if (size == 0xfff) { if (records.remaining() < 4) throw invalid(); size = records.getInt(); }
                    if (size < 0 || size > records.remaining()) throw invalid();
                    if (tag == 67) {
                        if ((size & 1) != 0) throw invalid();
                        int end = records.position() + size;
                        while (records.position() < end) {
                            char c = records.getChar();
                            if (c == 10 || c == 13) output.append('\n');
                            else if (c >= 32) output.append(c);
                            else if (c == 9) { output.append(' '); if (records.position() + 14 > end) throw invalid(); records.position(records.position() + 14); }
                            // 확장·인라인 제어 문자(1~8, 11~12, 14~23)는 뒤 7글자(14바이트)를 함께 건너뛴다.
                            else if (Set.of(1,2,3,4,5,6,7,8,11,12,14,15,16,17,18,19,20,21,22,23).contains((int)c)) {
                                if (records.position() + 14 > end) throw invalid(); records.position(records.position() + 14);
                            }
                        }
                        output.append('\n');
                    } else records.position(records.position() + size);
                }
            }
            return output.toString();
        }
    }

    private static byte[] document(DirectoryEntry directory, String name) throws IOException {
        var entry = (DocumentEntry) directory.getEntry(name);
        if (entry.getSize() > MAX_EXPANDED) throw invalid();
        try (InputStream input = new DocumentInputStream(entry)) { return input.readNBytes(MAX_EXPANDED + 1); }
    }

    private static CodeFailure invalid() {
        return CodeFailure.invalid("file", "10MB 이하의 텍스트 이력서(Word·PDF·PPT·HWP·TXT)를 확인해 주세요.");
    }

    private static CodeFailure unsupported() {
        return CodeFailure.invalid("file", TYPE_MESSAGE);
    }

    private static CodeFailure tooLarge() {
        return CodeFailure.invalid("file", SIZE_MESSAGE);
    }

    private static final class LimitedWriter extends Writer {
        private final StringBuilder text = new StringBuilder();
        @Override public void write(char[] chars, int start, int length) throws IOException {
            if (text.length() + length > MAX_TEXT) throw new IOException("Text limit");
            text.append(chars, start, length);
        }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString() { return text.toString(); }
    }
}

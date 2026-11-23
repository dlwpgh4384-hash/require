package com.company.common.commonops;

import com.company.common.commonops.service.CandidateResumeDocumentReader;
import com.company.common.support.service.CodeFailure;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class CandidateResumeDocumentReaderTest {
    private final CandidateResumeDocumentReader reader = new CandidateResumeDocumentReader();
    private String read(String ext, byte[] bytes) {
        return reader.read(new MockMultipartFile("file", "resume." + ext, "application/octet-stream", bytes));
    }
    @Test void acceptsInclusiveTenMiBAndRejectsLargerOrEmpty() {
        byte[] bytes = new byte[CandidateResumeDocumentReader.MAX_BYTES];
        Arrays.fill(bytes, (byte)' ');
        byte[] text = "희망금액:650만원".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(text, 0, bytes, 0, text.length);
        assertThat(read("txt", bytes)).contains("650만원");
        assertThatThrownBy(() -> read("txt", Arrays.copyOf(bytes, bytes.length + 1))).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> read("txt", new byte[0])).isInstanceOf(CodeFailure.class);
    }
    @Test void readsRealWordDocument() throws Exception {
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("희망금액:650만원");
            document.write(output);
            assertThat(read("docx", output.toByteArray())).contains("희망금액:650만원");
            assertThatThrownBy(() -> read("pptx", output.toByteArray())).isInstanceOf(CodeFailure.class);
        }
    }
    @Test void readsRealPowerPointDocument() throws Exception {
        try (var document = new XMLSlideShow(); var output = new ByteArrayOutputStream()) {
            document.createSlide().createTextBox().setText("희망금액:650만원");
            document.write(output);
            assertThat(read("pptx", output.toByteArray())).contains("650만원");
        }
    }
    @Test void readsRealLegacyPowerPointDocument() throws Exception {
        try (var document = new HSLFSlideShow(); var output = new ByteArrayOutputStream()) {
            var text = new HSLFTextBox(); text.setText("희망금액:650만원");
            document.createSlide().addShape(text);
            document.write(output);
            assertThat(read("ppt", output.toByteArray())).contains("650만원");
        }
    }
    private byte[] pdf(boolean encrypted) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(); document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700); stream.showText("6500000"); stream.endText();
            }
            if (encrypted) document.protect(new StandardProtectionPolicy("owner", "user", new AccessPermission()));
            document.save(output); return output.toByteArray();
        }
    }
    @Test void readsActualPdfAndRejectsEncryption() throws Exception {
        assertThat(read("pdf", pdf(false))).contains("6500000");
        assertThatThrownBy(() -> read("pdf", pdf(true))).isInstanceOf(CodeFailure.class);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void hwpFiveHasActualCompressedAndUncompressedRecordExtraction(boolean compressed) throws Exception {
        byte[] bytes = hwp(compressed, false);
        assertThat(read("hwp", bytes)).contains("희망금액:650만원");
        assertThatThrownBy(() -> read("doc", bytes)).isInstanceOf(CodeFailure.class);
    }
    @Test void encryptedHwpAndDamagedDocumentsAreRejected() throws Exception {
        assertThatThrownBy(() -> read("hwp", hwp(false, true))).isInstanceOf(CodeFailure.class);
        for (String ext : new String[]{"doc", "docx", "ppt", "pptx", "hwp", "pdf"})
            assertThatThrownBy(() -> read(ext, "wrong format".getBytes(StandardCharsets.UTF_8))).isInstanceOf(CodeFailure.class);
    }
    private byte[] hwp(boolean compressed, boolean encrypted) throws Exception {
        byte[] header = new byte[256];
        System.arraycopy("HWP Document File".getBytes(StandardCharsets.US_ASCII), 0, header, 0, 17);
        header[35] = 5; header[36] = (byte)((compressed ? 1 : 0) | (encrypted ? 2 : 0));
        byte[] text = "희망금액:650만원".getBytes(StandardCharsets.UTF_16LE);
        byte[] body = ByteBuffer.allocate(4 + text.length).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(67 | (text.length << 20)).put(text).array();
        if (compressed) {
            var buffer = new ByteArrayOutputStream(); var deflater = new Deflater(6, true);
            try (var zip = new DeflaterOutputStream(buffer, deflater)) { zip.write(body); }
            finally { deflater.end(); }
            body = buffer.toByteArray();
        }
        try (var fs = new POIFSFileSystem(); var output = new ByteArrayOutputStream()) {
            fs.getRoot().createDocument("FileHeader", new ByteArrayInputStream(header));
            fs.getRoot().createDirectory("BodyText").createDocument("Section0", new ByteArrayInputStream(body));
            fs.writeFilesystem(output); return output.toByteArray();
        }
    }
    private byte[] xmlZip(String xml) throws Exception {
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml")); zip.write("<Types/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml")); zip.write(xml.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.finish(); return output.toByteArray();
        }
    }
    @Test void externalEntitiesAndRenamedImagesCannotBeParsed() throws Exception {
        byte[] xml = xmlZip("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///private/not-read'>]><document><t>&x;</t></document>");
        assertThatThrownBy(() -> read("docx", xml)).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> read("txt", new byte[]{(byte)0xff, (byte)0xd8, (byte)0xff})).isInstanceOf(CodeFailure.class);
    }
    @Test void mimeMismatchAndExcessiveExpandedTextAreRejected() throws Exception {
        assertThatThrownBy(() -> reader.read(new MockMultipartFile("file", "resume.txt", "image/jpeg", "text".getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> read("docx", xmlZip("<document><p><t>" + "a".repeat(200_001) + "</t></p></document>")))
            .isInstanceOf(CodeFailure.class);
    }
}

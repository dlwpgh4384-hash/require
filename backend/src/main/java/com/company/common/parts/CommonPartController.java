package com.company.common.parts;

import com.company.common.dto.UserContracts.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import static com.company.common.parts.PartContracts.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonPartController {
    private final CommonPartService service;
    private final ObjectMapper json;

    public CommonPartController(CommonPartService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }
    @GetMapping("/api/common/parts")
    public Response<Items<Part>> getCommonParts() { return Response.of(service.getCommonParts()); }
    @GetMapping("/api/common/parts/options")
    public Response<Options> getCommonPartOptions() { return Response.of(service.getCommonPartOptions()); }
    @GetMapping("/api/common/parts/{partId}/standards")
    public Response<Items<Standard>> getCommonPartStandards(@PathVariable String partId) {
        return Response.of(service.getCommonPartStandards(partId));
    }
    @GetMapping("/api/common/parts/{partId}/change-history")
    public Response<Items<Change>> getCommonPartChangeHistory(@PathVariable String partId) {
        return Response.of(service.getCommonPartChangeHistory(partId));
    }
    @PostMapping(value = "/api/common/parts", consumes = "application/json")
    public Response<Part> postCommonPart(@RequestBody String body) {
        return Response.of(service.postCommonPart(PartRules.create(parse(body))));
    }
    @PutMapping(value = "/api/common/parts/{partId}", consumes = "application/json")
    public Response<Part> putCommonPart(@PathVariable String partId, @RequestBody String body) {
        return Response.of(service.putCommonPart(partId, PartRules.rename(parse(body))));
    }
    @PutMapping(value = "/api/common/parts/{partId}/standards", consumes = "application/json")
    public Response<Items<Standard>> putCommonPartStandards(@PathVariable String partId, @RequestBody String body) {
        return Response.of(service.putCommonPartStandards(partId, PartRules.standard(parse(body))));
    }
    @PutMapping(value = "/api/common/parts/{partId}/stop", consumes = "application/json")
    public Response<Part> putCommonPartStop(@PathVariable String partId, @RequestBody String body) {
        return Response.of(service.putCommonPartStop(partId, PartRules.version(parse(body))));
    }
    private JsonNode parse(String body) {
        try {
            return json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body);
        } catch (JsonProcessingException failure) {
            throw PartRules.invalid("body", "입력 형식을 확인해 주세요.");
        }
    }
}

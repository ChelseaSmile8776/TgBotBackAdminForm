package com.example.bot.api;

import com.example.bot.domain.Submission;
import com.example.bot.repo.SubmissionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    private final SubmissionRepository repo;

    public ReportingController(SubmissionRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/range")
    public List<Submission> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return repo.findByDateRange(
                from.atStartOfDay().atOffset(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
    }

    @GetMapping(value = "/range.csv", produces = "text/csv")
    public @ResponseBody byte[] rangeCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {

        List<Submission> list = range(from, to);
        StringWriter sw = new StringWriter();
        try (CSVPrinter p = new CSVPrinter(sw, CSVFormat.DEFAULT.withHeader(
                "id","createdAt","formType","name","phone",
                "question","orderIdSql","sku","productUrl","amount","assignedToChatId"))) {
            for (Submission s : list) {
                p.printRecord(s.getId(), s.getCreatedAt(), s.getFormType(),
                        s.getName(), s.getPhone(), s.getQuestionText(),
                        s.getOrderIdSql(), s.getProductSku(), s.getProductUrl(),
                        s.getAmount(), s.getAssignedToChatId());
            }
        }
        return sw.toString().getBytes();
    }
}
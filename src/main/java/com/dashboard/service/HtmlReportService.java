package com.dashboard.service;

import com.dashboard.dto.response.FailedTestDetail;
import com.dashboard.dto.response.FlakyTestDto;
import com.dashboard.dto.response.RunSummaryResponse;
import com.dashboard.dto.response.SlowestTestDto;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class HtmlReportService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String generateReport(RunSummaryResponse summary, List<FlakyTestDto> flakyTests) {
        StringBuilder html = new StringBuilder();

        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Test Run Report</title>
                  <style>
                    *{box-sizing:border-box;margin:0;padding:0}
                    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f0f2f5;color:#333;padding:24px}
                    .header{background:#fff;padding:24px;border-radius:8px;margin-bottom:24px;box-shadow:0 1px 3px rgba(0,0,0,.1)}
                    .header h1{font-size:22px;color:#1a1a2e}
                    .header .meta{color:#666;margin-top:8px;font-size:13px}
                    .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:16px;margin-bottom:24px}
                    .card{background:#fff;padding:20px;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.1);text-align:center}
                    .card-value{font-size:38px;font-weight:700;line-height:1}
                    .card-label{font-size:12px;color:#666;margin-top:6px;text-transform:uppercase;letter-spacing:.5px}
                    .card.total .card-value{color:#2196F3}
                    .card.passed .card-value{color:#4CAF50}
                    .card.failed .card-value{color:#f44336}
                    .card.skipped .card-value{color:#FF9800}
                    .progress-row{background:#fff;padding:16px 24px;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.1);margin-bottom:24px;display:flex;align-items:center;gap:16px}
                    .progress-label{font-size:18px;font-weight:600;min-width:80px}
                    .progress-bar{flex:1;background:#e0e0e0;border-radius:4px;height:12px;overflow:hidden}
                    .progress-fill{height:100%;border-radius:4px;background:#4CAF50}
                    .progress-sub{font-size:13px;color:#666}
                    .section{background:#fff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.1);margin-bottom:24px;overflow:hidden}
                    .section-title{padding:16px 24px;border-bottom:1px solid #eee;font-size:15px;font-weight:600;color:#333}
                    table{width:100%;border-collapse:collapse}
                    th{background:#f8f9fa;padding:10px 20px;text-align:left;font-size:11px;text-transform:uppercase;color:#666;letter-spacing:.5px;border-bottom:1px solid #eee}
                    td{padding:11px 20px;border-bottom:1px solid #f5f5f5;font-size:13px}
                    tr:last-child td{border-bottom:none}
                    .badge{display:inline-block;padding:2px 9px;border-radius:10px;font-size:11px;font-weight:600}
                    .badge-failed{background:#ffebee;color:#f44336}
                    .badge-flaky{background:#fff3e0;color:#e65100}
                    .badge-slow{background:#e3f2fd;color:#1565c0}
                    .error-text{font-family:monospace;font-size:11px;color:#f44336;background:#ffebee;padding:3px 7px;border-radius:3px;display:inline-block;max-width:400px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                    .empty{padding:28px;text-align:center;color:#aaa;font-style:italic;font-size:13px}
                  </style>
                </head>
                <body>
                """);

        // Header
        String started = summary.getStartedAt() != null ? summary.getStartedAt().format(FORMATTER) : "N/A";
        html.append("<div class=\"header\">")
            .append("<h1>Test Run Report: <strong>").append(escape(summary.getRunId())).append("</strong></h1>")
            .append("<div class=\"meta\">")
            .append("Branch: <strong>").append(escape(summary.getBranch())).append("</strong>")
            .append(" &nbsp;|&nbsp; Environment: <strong>").append(escape(summary.getEnvironment())).append("</strong>")
            .append(" &nbsp;|&nbsp; Started: <strong>").append(started).append("</strong>")
            .append("</div></div>");

        // Summary cards
        html.append("<div class=\"cards\">")
            .append(card("total", summary.getTotal(), "Total Tests"))
            .append(card("passed", summary.getPassed(), "Passed"))
            .append(card("failed", summary.getFailed(), "Failed"))
            .append(card("skipped", summary.getSkipped(), "Skipped"))
            .append("</div>");

        // Pass-rate bar
        double pct = Math.min(summary.getPassRate(), 100.0);
        html.append("<div class=\"progress-row\">")
            .append("<span class=\"progress-label\">").append(summary.getPassRate()).append("%</span>")
            .append("<div class=\"progress-bar\"><div class=\"progress-fill\" style=\"width:").append(pct).append("%\"></div></div>")
            .append("<span class=\"progress-sub\">Pass rate &nbsp;·&nbsp; Avg duration: <strong>")
            .append(summary.getAvgDurationMs()).append(" ms</strong></span>")
            .append("</div>");

        // Failed tests
        html.append("<div class=\"section\"><div class=\"section-title\">Failed Tests (")
            .append(summary.getFailed()).append(")</div>");
        if (summary.getFailedTests() == null || summary.getFailedTests().isEmpty()) {
            html.append("<div class=\"empty\">No failed tests</div>");
        } else {
            html.append("<table><thead><tr>")
                .append("<th>Test ID</th><th>Test Name</th><th>Suite</th><th>Duration</th><th>Error</th>")
                .append("</tr></thead><tbody>");
            for (FailedTestDetail t : summary.getFailedTests()) {
                html.append("<tr>")
                    .append("<td>").append(escape(t.getTestId())).append("</td>")
                    .append("<td><span class=\"badge badge-failed\">FAILED</span> ").append(escape(t.getTestName())).append("</td>")
                    .append("<td>").append(escape(t.getSuite())).append("</td>")
                    .append("<td>").append(t.getDurationMs()).append(" ms</td>")
                    .append("<td>")
                    .append(t.getErrorMessage() != null
                            ? "<span class=\"error-text\">" + escape(t.getErrorMessage()) + "</span>"
                            : "<span style=\"color:#aaa\">—</span>")
                    .append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div>");

        // Slowest tests
        html.append("<div class=\"section\"><div class=\"section-title\">Slowest Tests (Top 5)</div>");
        if (summary.getSlowestTests() == null || summary.getSlowestTests().isEmpty()) {
            html.append("<div class=\"empty\">No data</div>");
        } else {
            html.append("<table><thead><tr><th>#</th><th>Test Name</th><th>Suite</th><th>Duration</th></tr></thead><tbody>");
            int rank = 1;
            for (SlowestTestDto t : summary.getSlowestTests()) {
                html.append("<tr>")
                    .append("<td>").append(rank++).append("</td>")
                    .append("<td><span class=\"badge badge-slow\">SLOW</span> ").append(escape(t.getTestName())).append("</td>")
                    .append("<td>").append(escape(t.getSuite())).append("</td>")
                    .append("<td><strong>").append(t.getDurationMs()).append(" ms</strong></td>")
                    .append("</tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div>");

        // Flaky tests
        html.append("<div class=\"section\"><div class=\"section-title\">Flaky Test Candidates (")
            .append(flakyTests.size()).append(")</div>");
        if (flakyTests.isEmpty()) {
            html.append("<div class=\"empty\">No flaky tests detected</div>");
        } else {
            html.append("<table><thead><tr><th>Test ID</th><th>Test Name</th><th>Suite</th><th>Passes</th><th>Failures</th></tr></thead><tbody>");
            for (FlakyTestDto t : flakyTests) {
                html.append("<tr>")
                    .append("<td>").append(escape(t.getTestId())).append("</td>")
                    .append("<td><span class=\"badge badge-flaky\">FLAKY</span> ").append(escape(t.getTestName())).append("</td>")
                    .append("<td>").append(escape(t.getSuite())).append("</td>")
                    .append("<td style=\"color:#4CAF50;font-weight:600\">").append(t.getPassCount()).append("</td>")
                    .append("<td style=\"color:#f44336;font-weight:600\">").append(t.getFailCount()).append("</td>")
                    .append("</tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div>");

        html.append("</body></html>");
        return html.toString();
    }

    private String card(String type, int value, String label) {
        return "<div class=\"card " + type + "\">"
                + "<div class=\"card-value\">" + value + "</div>"
                + "<div class=\"card-label\">" + label + "</div>"
                + "</div>";
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

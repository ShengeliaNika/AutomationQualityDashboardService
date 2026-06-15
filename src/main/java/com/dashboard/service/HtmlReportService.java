package com.dashboard.service;

import com.dashboard.dto.response.*;
import com.dashboard.enums.TestStatus;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HtmlReportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ─────────────────────────────────────────────────────────────
    // MAIN DASHBOARD  –  GET /
    // ─────────────────────────────────────────────────────────────
    public String generateMainDashboard(List<ProjectSummaryDto> projects) {
        long totalRuns    = projects.stream().mapToLong(ProjectSummaryDto::getTotalRuns).sum();
        long totalTests   = projects.stream().mapToLong(ProjectSummaryDto::getTotalTests).sum();
        long totalFailed  = projects.stream().mapToLong(ProjectSummaryDto::getTotalFailed).sum();
        double overallPass = totalTests > 0 ? round2((double)(totalTests - totalFailed) / totalTests * 100) : 0;

        StringBuilder h = new StringBuilder();
        h.append(htmlHead("QA Dashboard"));
        h.append("<body>");

        // ── top nav
        h.append("<nav class=\"topbar\">")
         .append("<span class=\"topbar-brand\">&#9654; QA Dashboard</span>")
         .append("<span class=\"topbar-sub\">Automation Quality Reporting</span>")
         .append("</nav>");

        h.append("<div class=\"page\">");

        // ── overview strip
        h.append("<div class=\"overview-strip\">")
         .append(overviewTile(projects.size(), "Projects"))
         .append(overviewTile((int) totalRuns,   "Total Runs"))
         .append(overviewTile((int) totalTests,  "Total Tests"))
         .append(overviewTileRate(overallPass,   "Overall Pass Rate"))
         .append("</div>");

        // ── project cards
        h.append("<h2 class=\"section-heading\">Projects</h2>");
        if (projects.isEmpty()) {
            h.append("<div class=\"empty-state\">No projects yet. Run your first test suite to get started.</div>");
        } else {
            h.append("<div class=\"project-grid\">");
            for (ProjectSummaryDto p : projects) {
                h.append(projectCard(p));
            }
            h.append("</div>");
        }

        h.append("</div></body></html>");
        return h.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // PROJECT DETAIL  –  GET /projects/{name}
    // ─────────────────────────────────────────────────────────────
    public String generateProjectPage(String projectName, List<Map<String, Object>> runs, List<Map<String, Object>> flakyTests) {
        long totalRuns   = runs.size();
        long totalPassed = runs.stream().mapToLong(r -> toLong(r.get("passed"))).sum();
        long totalFailed = runs.stream().mapToLong(r -> toLong(r.get("failed"))).sum();
        long totalTests  = runs.stream().mapToLong(r -> toLong(r.get("total"))).sum();
        double avgPass   = totalTests > 0 ? round2((double) totalPassed / totalTests * 100) : 0;

        StringBuilder h = new StringBuilder();
        h.append(htmlHead(projectName + " — Runs"));
        h.append("<body>");

        h.append("<nav class=\"topbar\">")
         .append("<a class=\"topbar-brand\" href=\"/\">&#9654; QA Dashboard</a>")
         .append("<span class=\"topbar-sep\">›</span>")
         .append("<span class=\"topbar-brand\">").append(escape(projectName)).append("</span>")
         .append("</nav>");

        h.append("<div class=\"page\">");

        // ── stats strip
        h.append("<div class=\"overview-strip\">")
         .append(overviewTile((int) totalRuns,   "Runs"))
         .append(overviewTile((int) totalTests,  "Total Tests"))
         .append(overviewTile((int) totalFailed, "Total Failed"))
         .append(overviewTileRate(avgPass,        "Avg Pass Rate"))
         .append("</div>");

        // ── flaky / frequently failing section
        if (!flakyTests.isEmpty()) {
            h.append("<h2 class=\"section-heading\">&#9888; Frequently Failing Tests <span style=\"font-size:13px;font-weight:400;color:#aaa\">(last 10 runs)</span></h2>");
            h.append("<div class=\"card-table\" style=\"margin-bottom:24px\">");
            h.append("<table><thead><tr><th>Suite</th><th>Test Name</th><th>Runs</th><th>Passed</th><th>Failed</th><th>Failure Rate</th></tr></thead><tbody>");
            for (Map<String, Object> ft : flakyTests) {
                double fr = toDouble(ft.get("failureRate"));
                String frColor = fr >= 50 ? "#f44336" : "#FF9800";
                h.append("<tr>")
                 .append("<td style=\"color:#888;font-size:12px\">").append(escape(str(ft.get("suite")))).append("</td>")
                 .append("<td><span class=\"badge badge-flaky\">FLAKY</span> ").append(escape(str(ft.get("testName")))).append("</td>")
                 .append("<td>").append(ft.get("totalRuns")).append("</td>")
                 .append("<td style=\"color:#4CAF50;font-weight:600\">").append(ft.get("passed")).append("</td>")
                 .append("<td style=\"color:#f44336;font-weight:600\">").append(ft.get("failed")).append("</td>")
                 .append("<td>")
                 .append("<div style=\"display:flex;align-items:center;gap:8px\">")
                 .append("<div style=\"flex:1;background:#eee;border-radius:4px;height:8px;min-width:80px\">")
                 .append("<div style=\"width:").append(fr).append("%;height:100%;border-radius:4px;background:").append(frColor).append("\"></div></div>")
                 .append("<span style=\"font-size:12px;font-weight:700;color:").append(frColor).append("\">").append(fr).append("%</span>")
                 .append("</div></td>")
                 .append("</tr>");
            }
            h.append("</tbody></table></div>");
        }

        // ── action bar (hidden until rows selected)
        h.append("<div id=\"action-bar\" style=\"display:none;align-items:center;gap:10px;background:#1a1a2e;color:#fff;padding:10px 16px;border-radius:8px;margin-bottom:12px\">")
         .append("<span id=\"sel-count\" style=\"font-size:13px;font-weight:600\"></span>")
         .append("<button onclick=\"compareSelected()\" id=\"btn-compare\" class=\"act-btn\" style=\"background:#2196F3\">&#9654; Compare Selected</button>")
         .append("<button onclick=\"deleteSelected()\" class=\"act-btn\" style=\"background:#f44336\">&#128465; Delete Selected</button>")
         .append("<button onclick=\"clearSelection()\" class=\"act-btn\" style=\"background:#555;margin-left:auto\">Clear</button>")
         .append("</div>");

        // ── runs table
        h.append("<h2 class=\"section-heading\">Test Runs</h2>");
        if (runs.isEmpty()) {
            h.append("<div class=\"empty-state\">No runs recorded for this project yet.</div>");
        } else {
            h.append("<div class=\"card-table\">");
            h.append("<table><thead><tr>")
             .append("<th><input type=\"checkbox\" id=\"chk-all\" onchange=\"selectAll(this)\"></th>")
             .append("<th>Started</th><th>Branch</th><th>Environment</th>")
             .append("<th>Tests</th><th>Passed</th><th>Failed</th><th>Pass Rate</th><th></th>")
             .append("</tr></thead><tbody>");

            for (Map<String, Object> r : runs) {
                long   total    = toLong(r.get("total"));
                long   passed   = toLong(r.get("passed"));
                long   failed   = toLong(r.get("failed"));
                double passRate = toDouble(r.get("passRate"));
                String started  = r.get("startedAt") != null ? r.get("startedAt").toString().replace("T", " ").substring(0, 16) : "—";
                String runId    = str(r.get("runId"));
                String reportUrl = (String) r.get("reportUrl");
                String rateColor = passRate >= 90 ? "#4CAF50" : passRate >= 70 ? "#FF9800" : "#f44336";

                h.append("<tr>")
                 .append("<td><input type=\"checkbox\" class=\"run-check\" value=\"").append(escape(runId)).append("\" onchange=\"updateActionBar()\"></td>")
                 .append("<td class=\"mono\">").append(escape(started)).append("</td>")
                 .append("<td><span class=\"tag\">").append(escape(str(r.get("branch")))).append("</span></td>")
                 .append("<td><span class=\"tag tag-env\">").append(escape(str(r.get("environment")))).append("</span></td>")
                 .append("<td>").append(total).append("</td>")
                 .append("<td style=\"color:#4CAF50;font-weight:600\">").append(passed).append("</td>")
                 .append("<td style=\"color:").append(failed > 0 ? "#f44336" : "#aaa").append(";font-weight:600\">").append(failed).append("</td>")
                 .append("<td>")
                 .append("<div style=\"display:flex;align-items:center;gap:8px\">")
                 .append("<div style=\"flex:1;background:#eee;border-radius:4px;height:8px;min-width:60px\">")
                 .append("<div style=\"width:").append(passRate).append("%;height:100%;border-radius:4px;background:").append(rateColor).append("\"></div></div>")
                 .append("<span style=\"font-size:12px;font-weight:600;color:").append(rateColor).append("\">").append(passRate).append("%</span>")
                 .append("</div></td>")
                 .append("<td><a class=\"btn-report\" href=\"").append(escape(reportUrl)).append("\">View Report</a></td>")
                 .append("</tr>");
            }
            h.append("</tbody></table></div>");
        }

        // ── inline JS
        h.append("<style>.act-btn{padding:6px 14px;border:none;border-radius:5px;color:#fff;font-size:13px;font-weight:600;cursor:pointer}.act-btn:hover{opacity:.85}</style>");
        h.append("<script>");
        h.append("const PROJECT='").append(escape(projectName)).append("';");
        h.append("""
            function updateActionBar(){
                const checked=document.querySelectorAll('.run-check:checked');
                const n=checked.length;
                document.getElementById('action-bar').style.display=n>0?'flex':'none';
                document.getElementById('sel-count').textContent=n+' run'+(n===1?'':'s')+' selected';
                document.getElementById('btn-compare').disabled=n<2;
            }
            function selectAll(cb){
                document.querySelectorAll('.run-check').forEach(c=>c.checked=cb.checked);
                updateActionBar();
            }
            function clearSelection(){
                document.querySelectorAll('.run-check').forEach(c=>c.checked=false);
                const ca=document.getElementById('chk-all');
                if(ca)ca.checked=false;
                updateActionBar();
            }
            function deleteSelected(){
                const ids=[...document.querySelectorAll('.run-check:checked')].map(c=>c.value);
                if(!confirm('Delete '+ids.length+' run(s)? This cannot be undone.'))return;
                fetch('/runs/batch-delete',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(ids)})
                    .then(()=>location.reload());
            }
            function compareSelected(){
                const ids=[...document.querySelectorAll('.run-check:checked')].map(c=>c.value);
                if(ids.length<2){alert('Select at least 2 runs to compare.');return;}
                location.href='/projects/'+PROJECT+'/combined?'+ids.map(id=>'runIds='+encodeURIComponent(id)).join('&');
            }
        """);
        h.append("</script>");

        h.append("</div></body></html>");
        return h.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // RUN DETAIL REPORT  –  GET /runs/{id}/report
    // ─────────────────────────────────────────────────────────────
    public String generateReport(RunSummaryResponse summary, List<FlakyTestDto> flakyTests) {
        return generateReport(summary, flakyTests, false);
    }

    public String generateReport(RunSummaryResponse summary, List<FlakyTestDto> flakyTests, boolean download) {
        StringBuilder h = new StringBuilder();
        String title = (summary.getProjectName() != null ? summary.getProjectName() + " — " : "") + "Run Report";
        h.append(htmlHead(title));
        h.append("<body>");

        if (download) {
            // ── download: clean header, no navigation
            String proj        = summary.getProjectName() != null ? summary.getProjectName() : "QA";
            String headerDate  = summary.getStartedAt()   != null ? summary.getStartedAt().format(FMT) : "N/A";
            h.append("<div style=\"background:#1a1a2e;color:#fff;padding:20px 32px;display:flex;align-items:center;justify-content:space-between\">")
             .append("<div>")
             .append("<div style=\"font-size:20px;font-weight:700\">").append(escape(proj)).append(" — Test Run Report</div>")
             .append("<div style=\"font-size:13px;color:#aaa;margin-top:4px\">").append(headerDate).append("</div>")
             .append("</div>")
             .append("<div style=\"text-align:right;font-size:12px;color:#888\">")
             .append("<div>Generated by QA Dashboard</div>")
             .append("<div style=\"margin-top:2px;font-family:monospace;font-size:11px\">").append(escape(summary.getRunId())).append("</div>")
             .append("</div>")
             .append("</div>");
        } else {
            // ── online: breadcrumb nav with download button
            h.append("<nav class=\"topbar\">")
             .append("<a class=\"topbar-brand\" href=\"/\">&#9654; QA Dashboard</a>");
            if (summary.getProjectName() != null) {
                h.append("<span class=\"topbar-sep\">›</span>")
                 .append("<a class=\"topbar-brand\" href=\"/projects/").append(escape(summary.getProjectName())).append("\">")
                 .append(escape(summary.getProjectName())).append("</a>");
            }
            h.append("<span class=\"topbar-sep\">›</span>")
             .append("<span class=\"topbar-brand mono\" style=\"font-size:12px\">").append(escape(summary.getRunId())).append("</span>")
             .append("<a href=\"/runs/").append(escape(summary.getRunId())).append("/download\"")
             .append(" style=\"margin-left:auto;display:flex;align-items:center;gap:6px;padding:6px 14px;background:#2196F3;color:#fff;border-radius:5px;font-size:12px;font-weight:600;text-decoration:none\"")
             .append(" title=\"Download full report as HTML file\">&#11123; Download Report</a>")
             .append("</nav>");
        }

        h.append("<div class=\"page\">");

        // ── run meta
        String started = summary.getStartedAt() != null ? summary.getStartedAt().format(FMT) : "N/A";
        h.append("<div class=\"run-meta\">");
        h.append("<div><span class=\"meta-label\">Branch</span><span class=\"tag\">").append(escape(summary.getBranch())).append("</span></div>");
        h.append("<div><span class=\"meta-label\">Environment</span><span class=\"tag tag-env\">").append(escape(summary.getEnvironment())).append("</span></div>");
        h.append("<div><span class=\"meta-label\">Started</span><span class=\"mono\">").append(started).append("</span></div>");
        h.append("<div><span class=\"meta-label\">Avg Duration</span><span class=\"mono\">").append(summary.getAvgDurationMs()).append(" ms</span></div>");
        if (summary.getTargetUrl() != null) {
            h.append("<div><span class=\"meta-label\">Target URL</span><span class=\"mono\" style=\"color:#1565c0;font-size:11px\">").append(escape(summary.getTargetUrl())).append("</span></div>");
        }
        if (summary.getUsername() != null) {
            h.append("<div><span class=\"meta-label\">User</span><span class=\"tag\" style=\"background:#f3e5f5;color:#6a1b9a\">").append(escape(summary.getUsername())).append("</span></div>");
            h.append("<div><span class=\"meta-label\">Password</span><span class=\"mono\" style=\"color:#aaa;letter-spacing:2px\">&#9679;&#9679;&#9679;&#9679;&#9679;&#9679;&#9679;&#9679;</span></div>");
        }
        h.append("</div>");

        // ── summary cards
        h.append("<div class=\"overview-strip\">")
         .append(overviewTile(summary.getTotal(),   "Total"))
         .append(overviewTile(summary.getPassed(),  "Passed",  "#4CAF50"))
         .append(overviewTile(summary.getFailed(),  "Failed",  summary.getFailed() > 0 ? "#f44336" : "#4CAF50"))
         .append(overviewTile(summary.getSkipped(), "Skipped", "#FF9800"))
         .append(overviewTileRate(summary.getPassRate(), "Pass Rate"))
         .append("</div>");

        // ── charts row: donut + suite breakdown
        h.append("<div style=\"display:grid;grid-template-columns:1fr 2fr;gap:16px;margin-bottom:20px\">");
        h.append(donutChart(summary.getPassed(), summary.getFailed(), summary.getSkipped(), summary.getTotal()));
        h.append(suiteBreakdown(summary.getAllTests()));
        h.append("</div>");

        // ── all tests — filterable + sortable
        List<TestResultDetail> allTests = summary.getAllTests() != null ? summary.getAllTests() : List.of();
        long cntPassed  = allTests.stream().filter(t -> t.getStatus() == TestStatus.PASSED).count();
        long cntFailed  = allTests.stream().filter(t -> t.getStatus() == TestStatus.FAILED).count();
        long cntSkipped = allTests.stream().filter(t -> t.getStatus() == TestStatus.SKIPPED).count();

        h.append("<div class=\"section-card\" style=\"margin-bottom:20px\">");
        h.append("<div class=\"section-card-title\" style=\"display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px\">");
        h.append("<span>All Tests <span class=\"count-badge\">").append(summary.getTotal()).append("</span></span>");
        h.append("<div style=\"display:flex;gap:7px;align-items:center;flex-wrap:wrap\">");
        h.append(fltBtn("ALL",     "All",     summary.getTotal(), true));
        h.append(fltBtn("PASSED",  "Passed",  (int) cntPassed,   false));
        h.append(fltBtn("FAILED",  "Failed",  (int) cntFailed,   false));
        if (cntSkipped > 0) h.append(fltBtn("SKIPPED", "Skipped", (int) cntSkipped, false));
        h.append("<input id=\"test-search\" type=\"text\" placeholder=\"&#128269; Search tests...\" oninput=\"searchTests(this.value)\"")
         .append(" style=\"padding:5px 10px;border:1px solid #ddd;border-radius:16px;font-size:12px;width:190px;outline:none\">");
        h.append("</div></div>");

        if (allTests.isEmpty()) {
            h.append("<div class=\"empty-state\" style=\"padding:20px\">No test data</div>");
        } else {
            h.append("<div id=\"no-results\" style=\"display:none;padding:24px;text-align:center;color:#aaa;font-size:13px\">No tests match the current filter.</div>");
            h.append("<table id=\"tests-table\"><thead><tr>")
             .append("<th style=\"width:32px\"></th>")
             .append("<th>Suite</th><th>Test Name</th><th>Status</th>")
             .append("<th style=\"cursor:pointer;user-select:none;white-space:nowrap\" onclick=\"sortByDuration(this)\">")
             .append("Duration <span id=\"sort-arrow\" style=\"color:#bbb\">&#8597;</span></th>")
             .append("</tr></thead><tbody id=\"tests-tbody\">");

            for (TestResultDetail test : allTests) {
                String statusColor = switch (test.getStatus()) {
                    case PASSED  -> "#4CAF50";
                    case FAILED  -> "#f44336";
                    default      -> "#FF9800";
                };
                String statusLabel = test.getStatus().name();
                String statusWord  = statusLabel.charAt(0) + statusLabel.substring(1).toLowerCase();
                boolean hasDetails = (test.getErrorDetails() != null && !test.getErrorDetails().isBlank())
                                  || (test.getErrorMessage() != null && !test.getErrorMessage().isBlank());
                String safeNameLower = escape(test.getTestName()).toLowerCase();

                // build clipboard text
                StringBuilder copyText = new StringBuilder();
                copyText.append(test.getTestName())
                        .append(" - ").append(statusWord)
                        .append(" - Duration ").append(test.getDurationMs()).append(" ms");
                if (test.getErrorDetails() != null && !test.getErrorDetails().isBlank()) {
                    for (String line : test.getErrorDetails().split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) copyText.append("\n").append(trimmed);
                    }
                }
                if (test.getErrorMessage() != null && !test.getErrorMessage().isBlank()) {
                    copyText.append("\n").append(test.getErrorMessage().trim());
                }

                h.append("<tr class=\"test-row\" data-status=\"").append(statusLabel).append("\"")
                 .append(" data-duration=\"").append(test.getDurationMs()).append("\"")
                 .append(" data-name=\"").append(safeNameLower).append("\">");
                h.append("<td style=\"padding:8px 6px 8px 14px\">")
                 .append("<button class=\"copy-btn\" title=\"Copy test report\" onclick=\"copyTest(this)\"")
                 .append(" data-copy=\"").append(escape(copyText.toString())).append("\">&#128203;</button>")
                 .append("</td>");
                h.append("<td style=\"color:#888;font-size:12px\">").append(escape(test.getSuite())).append("</td>");
                h.append("<td>");
                if (hasDetails) {
                    h.append("<details><summary style=\"cursor:pointer;list-style:none;display:flex;align-items:center;gap:6px\">")
                     .append("<span style=\"font-size:11px;color:#bbb\">&#9654;</span>")
                     .append(escape(test.getTestName())).append("</summary>")
                     .append("<div class=\"test-details\">");
                    if (test.getErrorDetails() != null && !test.getErrorDetails().isBlank()) {
                        h.append("<div class=\"steps-list\">");
                        for (String line : test.getErrorDetails().split("\n")) {
                            String trimmed = line.trim();
                            if (trimmed.isEmpty()) continue;
                            boolean isFail = trimmed.contains("-> FAILED") || trimmed.startsWith("✗");
                            h.append("<div class=\"step ").append(isFail ? "step-fail" : "step-pass").append("\">")
                             .append(escape(trimmed)).append("</div>");
                        }
                        h.append("</div>");
                    }
                    if (test.getErrorMessage() != null && !test.getErrorMessage().isBlank()) {
                        h.append("<div class=\"error-block\">").append(escape(test.getErrorMessage())).append("</div>");
                    }
                    if (test.getRequestUrl() != null) {
                        h.append("<div class=\"req-resp-block\">");
                        h.append("<div class=\"req-resp-label\">Request</div>");
                        h.append("<div class=\"req-resp-method\"><span class=\"http-method\">").append(escape(test.getRequestMethod() != null ? test.getRequestMethod() : "")).append("</span> ").append(escape(test.getRequestUrl())).append("</div>");
                        if (test.getRequestBody() != null && !test.getRequestBody().isBlank()) {
                            h.append("<pre class=\"req-resp-body\">").append(escape(test.getRequestBody())).append("</pre>");
                        }
                        h.append("</div>");
                    }
                    if (test.getResponseStatus() != null) {
                        String respColor = test.getResponseStatus() >= 400 ? "#f44336" : "#4CAF50";
                        h.append("<div class=\"req-resp-block\">");
                        h.append("<div class=\"req-resp-label\">Response <span style=\"font-weight:700;color:").append(respColor).append("\">").append(test.getResponseStatus()).append("</span></div>");
                        if (test.getResponseBody() != null && !test.getResponseBody().isBlank()) {
                            h.append("<pre class=\"req-resp-body\">").append(escape(test.getResponseBody())).append("</pre>");
                        }
                        h.append("</div>");
                    }
                    h.append("</div></details>");
                } else {
                    h.append(escape(test.getTestName()));
                }
                h.append("</td>");
                h.append("<td><span style=\"font-size:11px;font-weight:700;color:").append(statusColor).append("\">").append(statusLabel).append("</span></td>");
                h.append("<td class=\"mono\">").append(test.getDurationMs()).append(" ms</td>");
                h.append("</tr>");
            }
            h.append("</tbody></table>");
        }
        h.append("</div>");

        // ── filter/sort JS
        h.append("<script>");
        h.append("""
            function filterTests(btn) {
                document.querySelectorAll('.flt-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                applyFilters();
            }
            function searchTests(q) { applyFilters(); }
            function applyFilters() {
                const f   = (document.querySelector('.flt-btn.active') || {dataset:{filter:'ALL'}}).dataset.filter;
                const q   = (document.getElementById('test-search')?.value || '').toLowerCase();
                let   vis = 0;
                document.querySelectorAll('.test-row').forEach(r => {
                    const ok = (f === 'ALL' || r.dataset.status === f) && r.dataset.name.includes(q);
                    r.style.display = ok ? '' : 'none';
                    if (ok) vis++;
                });
                const nr = document.getElementById('no-results');
                if (nr) nr.style.display = vis === 0 ? 'block' : 'none';
            }
            let _sortAsc = null;
            function sortByDuration(th) {
                _sortAsc = _sortAsc === null ? false : !_sortAsc;
                const tbody = document.getElementById('tests-tbody');
                [...tbody.querySelectorAll('tr.test-row')]
                    .sort((a,b) => _sortAsc
                        ? parseInt(a.dataset.duration)-parseInt(b.dataset.duration)
                        : parseInt(b.dataset.duration)-parseInt(a.dataset.duration))
                    .forEach(r => tbody.appendChild(r));
                document.getElementById('sort-arrow').innerHTML = _sortAsc ? '&#8593;' : '&#8595;';
            }
            function copyTest(btn) {
                const text = btn.dataset.copy;
                navigator.clipboard.writeText(text).then(() => {
                    const orig = btn.innerHTML;
                    btn.innerHTML = '&#10003;';
                    btn.style.color = '#4CAF50';
                    btn.style.background = '#f1f8f1';
                    setTimeout(() => { btn.innerHTML = orig; btn.style.color=''; btn.style.background=''; }, 1500);
                });
            }
        """);
        h.append("</script>");

        // ── failed tests
        h.append(tableSection("Failed Tests", summary.getFailed(), () -> {
            if (summary.getFailedTests() == null || summary.getFailedTests().isEmpty())
                return "<div class=\"empty-state\" style=\"padding:20px\">No failures &#10003;</div>";
            StringBuilder t = new StringBuilder();
            t.append("<table><thead><tr><th>Suite</th><th>Test Name</th><th>Duration</th><th>Error</th></tr></thead><tbody>");
            for (FailedTestDetail f : summary.getFailedTests()) {
                t.append("<tr>")
                 .append("<td>").append(escape(f.getSuite())).append("</td>")
                 .append("<td><span class=\"badge badge-failed\">FAIL</span> ").append(escape(f.getTestName())).append("</td>")
                 .append("<td class=\"mono\">").append(f.getDurationMs()).append(" ms</td>")
                 .append("<td>").append(f.getErrorMessage() != null
                         ? "<span class=\"error-text\">" + escape(f.getErrorMessage()) + "</span>"
                         : "<span style=\"color:#aaa\">—</span>").append("</td>")
                 .append("</tr>");
            }
            t.append("</tbody></table>");
            return t.toString();
        }));

        // ── slowest tests
        h.append(tableSection("Slowest Tests (Top 5)", null, () -> {
            if (summary.getSlowestTests() == null || summary.getSlowestTests().isEmpty())
                return "<div class=\"empty-state\" style=\"padding:20px\">No data</div>";
            StringBuilder t = new StringBuilder();
            t.append("<table><thead><tr><th>#</th><th>Suite</th><th>Test Name</th><th>Duration</th></tr></thead><tbody>");
            int rank = 1;
            for (SlowestTestDto s : summary.getSlowestTests()) {
                t.append("<tr>")
                 .append("<td style=\"color:#aaa\">").append(rank++).append("</td>")
                 .append("<td>").append(escape(s.getSuite())).append("</td>")
                 .append("<td><span class=\"badge badge-slow\">SLOW</span> ").append(escape(s.getTestName())).append("</td>")
                 .append("<td class=\"mono\"><strong>").append(s.getDurationMs()).append(" ms</strong></td>")
                 .append("</tr>");
            }
            t.append("</tbody></table>");
            return t.toString();
        }));

        // ── flaky tests
        h.append(tableSection("Flaky Test Candidates", flakyTests.size(), () -> {
            if (flakyTests.isEmpty())
                return "<div class=\"empty-state\" style=\"padding:20px\">No flaky tests detected</div>";
            StringBuilder t = new StringBuilder();
            t.append("<table><thead><tr><th>Suite</th><th>Test Name</th><th>Passes</th><th>Failures</th></tr></thead><tbody>");
            for (FlakyTestDto f : flakyTests) {
                t.append("<tr>")
                 .append("<td>").append(escape(f.getSuite())).append("</td>")
                 .append("<td><span class=\"badge badge-flaky\">FLAKY</span> ").append(escape(f.getTestName())).append("</td>")
                 .append("<td style=\"color:#4CAF50;font-weight:600\">").append(f.getPassCount()).append("</td>")
                 .append("<td style=\"color:#f44336;font-weight:600\">").append(f.getFailCount()).append("</td>")
                 .append("</tr>");
            }
            t.append("</tbody></table>");
            return t.toString();
        }));

        h.append("</div></body></html>");
        return h.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // COMBINED REPORT  –  GET /projects/{name}/combined
    // ─────────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public String generateCombinedReport(String projectName, Map<String, Object> report) {
        int    totalRuns    = (int)    report.get("totalRuns");
        int    uniqueTests  = (int)    report.get("totalUniqueTests");
        long   executions   = toLong(  report.get("totalExecutions"));
        double passRate     = toDouble(report.get("overallPassRate"));
        List<Map<String, Object>> tests = (List<Map<String, Object>>) report.get("tests");

        long totalFailed = tests.stream().mapToLong(t -> toLong(t.get("failed"))).sum();
        long totalPassed = tests.stream().mapToLong(t -> toLong(t.get("passed"))).sum();

        StringBuilder h = new StringBuilder();
        h.append(htmlHead("Combined Report — " + projectName));
        h.append("<body>");

        h.append("<nav class=\"topbar\">")
         .append("<a class=\"topbar-brand\" href=\"/\">&#9654; QA Dashboard</a>")
         .append("<span class=\"topbar-sep\">›</span>")
         .append("<a class=\"topbar-brand\" href=\"/projects/").append(escape(projectName)).append("\">").append(escape(projectName)).append("</a>")
         .append("<span class=\"topbar-sep\">›</span>")
         .append("<span class=\"topbar-brand\">Combined Report (").append(totalRuns).append(" runs)</span>")
         .append("</nav>");

        h.append("<div class=\"page\">");

        // ── stats strip
        h.append("<div class=\"overview-strip\">")
         .append(overviewTile(totalRuns,         "Runs Compared"))
         .append(overviewTile(uniqueTests,        "Unique Tests"))
         .append(overviewTile((int) totalPassed,  "Total Passed",  "#4CAF50"))
         .append(overviewTile((int) totalFailed,  "Total Failed",  totalFailed > 0 ? "#f44336" : "#4CAF50"))
         .append(overviewTileRate(passRate,        "Overall Pass Rate"))
         .append("</div>");

        // ── donut for overall
        h.append("<div style=\"display:grid;grid-template-columns:1fr 2fr;gap:16px;margin-bottom:24px\">");
        h.append(donutChart((int) totalPassed, (int) totalFailed, (int)(executions - totalPassed - totalFailed), (int) executions));

        // ── mini heat map by test failure rate
        h.append("<div class=\"chart-card\"><div class=\"chart-title\">Test Reliability (sorted by failure rate)</div>");
        for (Map<String, Object> t : tests) {
            double fr = toDouble(t.get("passRate"));
            String color = fr >= 90 ? "#4CAF50" : fr >= 70 ? "#FF9800" : "#f44336";
            h.append("<div style=\"margin-bottom:6px\">")
             .append("<div style=\"display:flex;justify-content:space-between;font-size:12px;margin-bottom:2px\">")
             .append("<span style=\"overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:70%\">").append(escape(str(t.get("testName")))).append("</span>")
             .append("<span style=\"font-weight:700;color:").append(color).append("\">").append(fr).append("%</span></div>")
             .append("<div style=\"background:#eee;border-radius:3px;height:7px\">")
             .append("<div style=\"width:").append(fr).append("%;height:100%;border-radius:3px;background:").append(color).append("\"></div></div>")
             .append("</div>");
        }
        h.append("</div>");
        h.append("</div>");

        // ── tests table
        h.append("<h2 class=\"section-heading\">All Tests Across Selected Runs</h2>");
        h.append("<div class=\"card-table\">");
        h.append("<table><thead><tr>")
         .append("<th>Suite</th><th>Test Name</th><th>Runs</th><th>Passed</th><th>Failed</th><th>Pass Rate</th><th>Avg Duration</th>")
         .append("</tr></thead><tbody>");

        for (Map<String, Object> t : tests) {
            long   failed   = toLong(  t.get("failed"));
            double pr       = toDouble(t.get("passRate"));
            String prColor  = pr >= 90 ? "#4CAF50" : pr >= 70 ? "#FF9800" : "#f44336";
            String rowStyle = failed > 0 ? " style=\"background:#fff8f8\"" : "";

            h.append("<tr").append(rowStyle).append(">")
             .append("<td style=\"color:#888;font-size:12px\">").append(escape(str(t.get("suite")))).append("</td>")
             .append("<td>")
             .append(failed > 0 ? "<span class=\"badge badge-failed\">FAIL</span> " : "<span style=\"color:#4CAF50;font-size:11px;font-weight:700;margin-right:4px\">&#10003;</span>")
             .append(escape(str(t.get("testName")))).append("</td>")
             .append("<td style=\"color:#aaa\">").append(t.get("runs")).append("</td>")
             .append("<td style=\"color:#4CAF50;font-weight:600\">").append(t.get("passed")).append("</td>")
             .append("<td style=\"color:").append(failed > 0 ? "#f44336" : "#aaa").append(";font-weight:600\">").append(failed).append("</td>")
             .append("<td>")
             .append("<div style=\"display:flex;align-items:center;gap:8px\">")
             .append("<div style=\"flex:1;background:#eee;border-radius:4px;height:8px;min-width:60px\">")
             .append("<div style=\"width:").append(pr).append("%;height:100%;border-radius:4px;background:").append(prColor).append("\"></div></div>")
             .append("<span style=\"font-size:12px;font-weight:700;color:").append(prColor).append("\">").append(pr).append("%</span>")
             .append("</div></td>")
             .append("<td class=\"mono\">").append(t.get("avgDurationMs")).append(" ms</td>")
             .append("</tr>");
        }
        h.append("</tbody></table></div>");
        if (download) {
            h.append("<script>document.querySelectorAll('details').forEach(d=>d.open=true);</script>");
        }
        h.append("</div></body></html>");
        return h.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // SHARED HTML HELPERS
    // ─────────────────────────────────────────────────────────────
    private String htmlHead(String title) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>""" + escape(title) + """
              </title>
              <style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f0f2f5;color:#222}
                a{color:inherit;text-decoration:none}

                /* nav */
                .topbar{display:flex;align-items:center;gap:8px;background:#1a1a2e;color:#fff;padding:0 24px;height:50px;position:sticky;top:0;z-index:100}
                .topbar-brand{font-size:15px;font-weight:600;color:#fff;white-space:nowrap}
                .topbar-brand:hover{opacity:.8}
                .topbar-sep{color:#555;font-size:18px}
                .topbar-sub{margin-left:auto;font-size:12px;color:#888}

                /* layout */
                .page{max-width:1100px;margin:0 auto;padding:28px 20px}
                .section-heading{font-size:16px;font-weight:600;color:#444;margin:28px 0 12px}

                /* overview strip */
                .overview-strip{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:14px;margin-bottom:24px}
                .ov-tile{background:#fff;border-radius:10px;padding:18px 16px;text-align:center;box-shadow:0 1px 4px rgba(0,0,0,.08)}
                .ov-value{font-size:34px;font-weight:700;line-height:1}
                .ov-label{font-size:11px;color:#888;margin-top:5px;text-transform:uppercase;letter-spacing:.5px}

                /* project cards */
                .project-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:18px}
                .proj-card{background:#fff;border-radius:10px;box-shadow:0 1px 4px rgba(0,0,0,.08);overflow:hidden;cursor:pointer;transition:box-shadow .15s}
                .proj-card:hover{box-shadow:0 4px 14px rgba(0,0,0,.14)}
                .proj-card-top{padding:18px 20px 12px;border-left:4px solid var(--accent,#2196F3)}
                .proj-card-name{font-size:16px;font-weight:700;color:#1a1a2e}
                .proj-card-last{font-size:12px;color:#aaa;margin-top:3px}
                .proj-card-stats{display:flex;gap:20px;padding:10px 20px 16px;border-top:1px solid #f5f5f5;margin-top:8px}
                .proj-stat{text-align:center}
                .proj-stat-val{font-size:20px;font-weight:700}
                .proj-stat-lbl{font-size:10px;color:#aaa;text-transform:uppercase}
                .sparkline{padding:0 20px 14px}

                /* run table */
                .card-table{background:#fff;border-radius:10px;box-shadow:0 1px 4px rgba(0,0,0,.08);overflow:hidden}
                table{width:100%;border-collapse:collapse}
                th{background:#f8f9fa;padding:10px 16px;text-align:left;font-size:11px;text-transform:uppercase;color:#888;letter-spacing:.5px;border-bottom:1px solid #eee}
                td{padding:11px 16px;border-bottom:1px solid #f8f8f8;font-size:13px;vertical-align:middle}
                tr:last-child td{border-bottom:none}
                tr:hover td{background:#fafafa}

                /* run report specific */
                .run-meta{display:flex;flex-wrap:wrap;gap:20px;background:#fff;border-radius:10px;padding:16px 20px;margin-bottom:20px;box-shadow:0 1px 4px rgba(0,0,0,.08);align-items:center}
                .meta-label{font-size:11px;color:#aaa;text-transform:uppercase;letter-spacing:.4px;margin-right:6px}
                .progress-row{background:#fff;border-radius:10px;padding:14px 20px;margin-bottom:20px;box-shadow:0 1px 4px rgba(0,0,0,.08)}
                .section-card{background:#fff;border-radius:10px;box-shadow:0 1px 4px rgba(0,0,0,.08);margin-bottom:20px;overflow:hidden}
                .section-card-title{padding:14px 20px;border-bottom:1px solid #f0f0f0;font-size:14px;font-weight:600;color:#333;display:flex;align-items:center;gap:8px}
                .count-badge{background:#eee;color:#555;font-size:11px;padding:2px 7px;border-radius:8px}

                /* misc */
                .tag{display:inline-block;padding:2px 8px;border-radius:4px;font-size:12px;background:#e8f0fe;color:#1565c0;font-weight:500}
                .tag-env{background:#e8f5e9;color:#2e7d32}
                .badge{display:inline-block;padding:2px 7px;border-radius:8px;font-size:11px;font-weight:600;margin-right:4px}
                .badge-failed{background:#ffebee;color:#c62828}
                .badge-flaky{background:#fff3e0;color:#e65100}
                .badge-slow{background:#e3f2fd;color:#1565c0}
                .error-text{font-family:monospace;font-size:11px;color:#c62828;background:#ffebee;padding:2px 6px;border-radius:3px;display:inline-block;max-width:360px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                .btn-report{display:inline-block;padding:5px 12px;border-radius:5px;background:#1a1a2e;color:#fff;font-size:12px;font-weight:600;white-space:nowrap}
                .btn-report:hover{background:#2d2d4e}
                .empty-state{text-align:center;padding:48px;color:#bbb;font-size:14px}
                .mono{font-family:monospace;font-size:12px}

                /* charts */
                .chart-card{background:#fff;border-radius:10px;box-shadow:0 1px 4px rgba(0,0,0,.08);padding:20px}
                .chart-title{font-size:13px;font-weight:600;color:#555;margin-bottom:14px}
                .donut-wrap{display:flex;align-items:center;gap:20px}
                .donut{width:110px;height:110px;border-radius:50%;flex-shrink:0}
                .legend{display:flex;flex-direction:column;gap:8px}
                .legend-item{display:flex;align-items:center;gap:7px;font-size:13px}
                .legend-dot{width:11px;height:11px;border-radius:50%;flex-shrink:0}
                .suite-bar-row{margin-bottom:8px}
                .suite-bar-label{font-size:12px;color:#555;margin-bottom:3px;display:flex;justify-content:space-between}
                .suite-bar-track{background:#f0f0f0;border-radius:4px;height:10px;overflow:hidden}
                .suite-bar-pass{height:100%;background:#4CAF50;float:left}
                .suite-bar-fail{height:100%;background:#f44336;float:left}

                /* copy button */
                .copy-btn{background:none;border:none;cursor:pointer;font-size:13px;padding:2px 4px;border-radius:4px;opacity:0;transition:opacity .15s,background .15s,color .15s;line-height:1}
                tr:hover .copy-btn{opacity:1}
                .copy-btn:hover{background:#e8f0fe}

                /* filter buttons */
                .flt-btn{padding:4px 12px;border-radius:16px;border:1.5px solid #ddd;background:#fff;font-size:12px;font-weight:500;cursor:pointer;transition:all .15s;white-space:nowrap}
                .flt-btn:hover:not(.active){background:#f5f5f5}
                .flt-btn.active{background:#1a1a2e;color:#fff;border-color:#1a1a2e}
                .flt-btn.flt-passed.active{background:#4CAF50;border-color:#4CAF50;color:#fff}
                .flt-btn.flt-failed.active{background:#f44336;border-color:#f44336;color:#fff}
                .flt-btn.flt-skipped.active{background:#FF9800;border-color:#FF9800;color:#fff}
                .flt-count{display:inline-block;background:rgba(0,0,0,.12);border-radius:8px;padding:0 5px;font-size:10px;margin-left:3px}
                th[onclick]:hover{background:#f0f0f0}

                /* expandable test rows */
                details summary::-webkit-details-marker{display:none}
                details>summary{border-bottom:1px solid #f0f0f0}
                details[open]>summary{border-bottom:1px solid #eee}
                .test-details{margin-top:10px;padding:12px;background:#fafafa;border-radius:6px;border-left:3px solid #ddd}
                .steps-list{display:flex;flex-direction:column;gap:3px;margin-bottom:8px}
                .step{font-family:monospace;font-size:12px;padding:3px 8px;border-radius:3px}
                .step-pass{background:#f1f8f1;color:#2e7d32}
                .step-fail{background:#ffebee;color:#c62828;font-weight:600}
                .error-block{font-family:monospace;font-size:11px;color:#c62828;background:#ffebee;padding:6px 10px;border-radius:4px;margin-top:6px;white-space:pre-wrap;word-break:break-word}
                .req-resp-block{margin-top:8px;border:1px solid #e0e0e0;border-radius:5px;overflow:hidden}
                .req-resp-label{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#888;background:#f8f8f8;padding:4px 8px;border-bottom:1px solid #e0e0e0}
                .req-resp-method{font-family:monospace;font-size:12px;padding:5px 8px;background:#fff;word-break:break-all}
                .http-method{display:inline-block;padding:1px 6px;border-radius:3px;background:#e3f2fd;color:#1565c0;font-weight:700;font-size:11px;margin-right:4px}
                .req-resp-body{font-family:monospace;font-size:11px;padding:6px 8px;margin:0;background:#fafafa;border-top:1px solid #eee;white-space:pre-wrap;word-break:break-word;max-height:200px;overflow-y:auto;color:#333}
              </style>
            </head>
            """;
    }

    private String overviewTile(int value, String label) {
        return overviewTile(value, label, "#1a1a2e");
    }

    private String overviewTile(int value, String label, String color) {
        return "<div class=\"ov-tile\"><div class=\"ov-value\" style=\"color:" + color + "\">" + value
                + "</div><div class=\"ov-label\">" + escape(label) + "</div></div>";
    }

    private String overviewTileRate(double rate, String label) {
        String color = rate >= 90 ? "#4CAF50" : rate >= 70 ? "#FF9800" : "#f44336";
        return "<div class=\"ov-tile\"><div class=\"ov-value\" style=\"color:" + color + "\">" + rate
                + "%</div><div class=\"ov-label\">" + escape(label) + "</div></div>";
    }

    private String projectCard(ProjectSummaryDto p) {
        double rate   = p.getAvgPassRate();
        String accent = rate >= 90 ? "#4CAF50" : rate >= 70 ? "#FF9800" : "#f44336";
        String last   = p.getLastRunAt() != null ? p.getLastRunAt().format(FMT) : "never";
        String spark  = sparkline(p.getPassRateTrend(), accent);

        return "<a href=\"/projects/" + escape(p.getProjectName()) + "\">"
             + "<div class=\"proj-card\" style=\"--accent:" + accent + "\">"
             + "<div class=\"proj-card-top\">"
             + "<div class=\"proj-card-name\">" + escape(p.getProjectName()) + "</div>"
             + "<div class=\"proj-card-last\">Last run: " + escape(last) + "</div>"
             + "</div>"
             + "<div class=\"proj-card-stats\">"
             + projStat(p.getTotalRuns(),   "Runs",   "#555")
             + projStat(p.getTotalTests(),  "Tests",  "#555")
             + projStat(p.getTotalFailed(), "Failed", p.getTotalFailed() > 0 ? "#f44336" : "#4CAF50")
             + projStat(rate + "%",         "Pass Rate", accent)
             + "</div>"
             + "<div class=\"sparkline\">" + spark + "</div>"
             + "</div></a>";
    }

    private String projStat(Object value, String label, String color) {
        return "<div class=\"proj-stat\">"
             + "<div class=\"proj-stat-val\" style=\"color:" + color + "\">" + value + "</div>"
             + "<div class=\"proj-stat-lbl\">" + label + "</div>"
             + "</div>";
    }

    /** SVG sparkline for pass rate trend (last 5 runs). */
    private String sparkline(List<Double> trend, String color) {
        if (trend == null || trend.size() < 2) {
            return "<svg width=\"100%\" height=\"32\"><text x=\"0\" y=\"20\" fill=\"#ccc\" font-size=\"11\">not enough data</text></svg>";
        }
        int w = 220, h = 32, n = trend.size();
        double minY = trend.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double maxY = trend.stream().mapToDouble(Double::doubleValue).max().orElse(100);
        if (maxY == minY) maxY = minY + 1;

        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double x = i * (w - 1.0) / (n - 1);
            double y = h - 4 - ((trend.get(i) - minY) / (maxY - minY)) * (h - 8);
            if (i > 0) pts.append(" ");
            pts.append(String.format("%.1f,%.1f", x, y));
        }
        return "<svg width=\"" + w + "\" height=\"" + h + "\" viewBox=\"0 0 " + w + " " + h + "\">"
             + "<polyline points=\"" + pts + "\" fill=\"none\" stroke=\"" + color + "\" stroke-width=\"2\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>"
             + "</svg>";
    }

    private String tableSection(String title, Integer count, java.util.function.Supplier<String> body) {
        String countHtml = count != null
                ? "<span class=\"count-badge\">" + count + "</span>"
                : "";
        return "<div class=\"section-card\">"
             + "<div class=\"section-card-title\">" + escape(title) + countHtml + "</div>"
             + body.get()
             + "</div>";
    }

    private String donutChart(int passed, int failed, int skipped, int total) {
        if (total == 0) return "<div class=\"chart-card\"><div class=\"chart-title\">Results</div><div class=\"empty-state\" style=\"padding:20px\">No data</div></div>";
        double pPct = (double) passed / total * 100;
        double fPct = (double) failed / total * 100;
        String gradient = String.format(
            "conic-gradient(#4CAF50 0%% %.1f%%, #f44336 %.1f%% %.1f%%, #FF9800 %.1f%% 100%%)",
            pPct, pPct, pPct + fPct, pPct + fPct);
        return "<div class=\"chart-card\">"
             + "<div class=\"chart-title\">Results Distribution</div>"
             + "<div class=\"donut-wrap\">"
             + "<div class=\"donut\" style=\"background:" + gradient + "\"></div>"
             + "<div class=\"legend\">"
             + legendItem("#4CAF50", "Passed",  passed,  total)
             + legendItem("#f44336", "Failed",  failed,  total)
             + legendItem("#FF9800", "Skipped", skipped, total)
             + "</div></div></div>";
    }

    private String legendItem(String color, String label, int count, int total) {
        double pct = total > 0 ? round2((double) count / total * 100) : 0;
        return "<div class=\"legend-item\">"
             + "<div class=\"legend-dot\" style=\"background:" + color + "\"></div>"
             + "<span><strong>" + count + "</strong> " + label + " <span style=\"color:#aaa\">(" + pct + "%)</span></span>"
             + "</div>";
    }

    private String suiteBreakdown(List<TestResultDetail> allTests) {
        if (allTests == null || allTests.isEmpty())
            return "<div class=\"chart-card\"><div class=\"chart-title\">By Suite</div><div class=\"empty-state\" style=\"padding:20px\">No data</div></div>";

        Map<String, long[]> suites = allTests.stream().collect(Collectors.groupingBy(
            t -> t.getSuite() != null ? t.getSuite() : "—",
            Collectors.collectingAndThen(Collectors.toList(), list -> new long[]{
                list.stream().filter(t -> t.getStatus() == TestStatus.PASSED).count(),
                list.stream().filter(t -> t.getStatus() == TestStatus.FAILED).count(),
                list.size()
            })));

        StringBuilder b = new StringBuilder();
        b.append("<div class=\"chart-card\" style=\"padding:0;overflow:hidden\">")
         .append("<details>")
         .append("<summary style=\"padding:16px 20px;cursor:pointer;list-style:none;display:flex;align-items:center;justify-content:space-between;user-select:none\">")
         .append("<span class=\"chart-title\" style=\"margin:0\">By Suite</span>")
         .append("<span style=\"font-size:12px;color:#aaa\">").append(suites.size()).append(" suites &nbsp;&#9660;</span>")
         .append("</summary>")
         .append("<div style=\"padding:0 20px 16px\">");
        suites.forEach((suite, counts) -> {
            long pass = counts[0], fail = counts[1], tot = counts[2];
            double passPct = tot > 0 ? round2((double) pass / tot * 100) : 0;
            double failPct = tot > 0 ? round2((double) fail / tot * 100) : 0;
            b.append("<div class=\"suite-bar-row\">")
             .append("<div class=\"suite-bar-label\"><span>").append(escape(suite)).append("</span>")
             .append("<span style=\"color:#aaa;font-size:11px\">").append(pass).append("/").append(tot).append("</span></div>")
             .append("<div class=\"suite-bar-track\">")
             .append("<div class=\"suite-bar-pass\" style=\"width:").append(passPct).append("%\"></div>")
             .append("<div class=\"suite-bar-fail\" style=\"width:").append(failPct).append("%\"></div>")
             .append("</div></div>");
        });
        b.append("</div></details></div>");
        return b.toString();
    }

    private String fltBtn(String filter, String label, int count, boolean active) {
        String activeClass = active ? " active" : "";
        String colorClass  = switch (filter) {
            case "PASSED"  -> " flt-passed";
            case "FAILED"  -> " flt-failed";
            case "SKIPPED" -> " flt-skipped";
            default        -> "";
        };
        return "<button class=\"flt-btn" + colorClass + activeClass + "\" data-filter=\"" + filter + "\" onclick=\"filterTests(this)\">"
             + label + " <span class=\"flt-count\">" + count + "</span></button>";
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }
    private long   toLong(Object o)   { return o == null ? 0 : ((Number) o).longValue(); }
    private double toDouble(Object o) { return o == null ? 0 : ((Number) o).doubleValue(); }
    private double round2(double v)   { return Math.round(v * 100.0) / 100.0; }
}

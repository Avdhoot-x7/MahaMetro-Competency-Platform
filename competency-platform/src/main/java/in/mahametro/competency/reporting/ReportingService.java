package in.mahametro.competency.reporting;

import in.mahametro.competency.access.AccessService;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.employees.Grade;
import in.mahametro.competency.employees.GradeRepository;
import in.mahametro.competency.employees.RoleFamily;
import in.mahametro.competency.employees.RoleFamilyRepository;
import in.mahametro.competency.scoring.Profile;
import in.mahametro.competency.scoring.ProfileRow;
import in.mahametro.competency.scoring.ProfileService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing stored profile summaries, high-performance dashboards,
 * server-side paging, and Excel reports.
 */
@Service
public class ReportingService {

    private final ProfileSummaryRepository summaries;
    private final EmployeeRepository employees;
    private final ProfileService profileService;
    private final AccessService access;
    private final RoleFamilyRepository roleFamilies;
    private final GradeRepository grades;

    public ReportingService(ProfileSummaryRepository summaries, EmployeeRepository employees,
            ProfileService profileService, AccessService access, RoleFamilyRepository roleFamilies,
            GradeRepository grades) {
        this.summaries = summaries;
        this.employees = employees;
        this.profileService = profileService;
        this.access = access;
        this.roleFamilies = roleFamilies;
        this.grades = grades;
    }

    /**
     * Refreshes the stored profile summary for a single employee.
     */
    @Transactional
    public ProfileSummary refreshSummary(Long employeeId) {
        Optional<Employee> empOpt = employees.findById(employeeId);
        if (empOpt.isEmpty() || empOpt.get().getRoleFamilyCode() == null || empOpt.get().getRoleFamilyCode().isBlank()) {
            summaries.findById(employeeId).ifPresent(summaries::delete);
            return null;
        }

        Employee emp = empOpt.get();
        Profile profile = profileService.build(emp);

        ProfileSummary summary = summaries.findById(employeeId)
                .orElseGet(() -> new ProfileSummary(emp.getId(), "Not assessed", null, 0, 0, 0));

        summary.setReadiness(profile.getReadiness());
        summary.setIndexPct(profile.getIndex());
        summary.setAssessedCount(profile.getAssessed());
        summary.setGapsCount(profile.getGaps());
        summary.setSafetyGates(profile.getSafetyGates());

        return summaries.save(summary);
    }

    /**
     * Rebuilds profile summaries for all employees with role families.
     */
    @Transactional
    public int refreshAllSummaries() {
        List<Employee> active = employees.findByActiveTrue().stream()
                .filter(e -> e.getRoleFamilyCode() != null && !e.getRoleFamilyCode().isBlank())
                .collect(Collectors.toList());

        if (active.isEmpty()) {
            return 0;
        }

        Map<Long, Profile> profileMap = profileService.buildAll(active);
        List<ProfileSummary> batch = new ArrayList<>();

        for (Employee e : active) {
            Profile p = profileMap.get(e.getId());
            if (p != null) {
                ProfileSummary s = summaries.findById(e.getId())
                        .orElseGet(() -> new ProfileSummary(e.getId(), "Not assessed", null, 0, 0, 0));
                s.setReadiness(p.getReadiness());
                s.setIndexPct(p.getIndex());
                s.setAssessedCount(p.getAssessed());
                s.setGapsCount(p.getGaps());
                s.setSafetyGates(p.getSafetyGates());
                batch.add(s);
            }
        }
        summaries.saveAll(batch);
        return batch.size();
    }

    /**
     * Returns paged staff results filtered by user scope and optional filter criteria.
     */
    @Transactional(readOnly = true)
    public Page<StaffSummaryRow> getPagedStaffResults(Employee viewer, StaffFilter filter, Pageable pageable) {
        List<StaffSummaryRow> allVisible = getFilteredSummaryRows(viewer, filter);

        int start = (int) pageable.getOffset();
        if (start >= allVisible.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, allVisible.size());
        }
        int end = Math.min(start + pageable.getPageSize(), allVisible.size());
        List<StaffSummaryRow> paged = allVisible.subList(start, end);

        return new PageImpl<>(paged, pageable, allVisible.size());
    }

    /**
     * Returns headline readiness breakdown (Red, Amber, Green, Total) for the visible audience.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> getHeadlineCounts(Employee viewer, StaffFilter filter) {
        StaffFilter baseFilter = new StaffFilter();
        baseFilter.setDepartment(filter.getDepartment());
        baseFilter.setRoleFamilyCode(filter.getRoleFamilyCode());
        baseFilter.setQ(filter.getQ());

        List<StaffSummaryRow> rows = getFilteredSummaryRows(viewer, baseFilter);
        int red = 0, amber = 0, green = 0, notAssessed = 0;
        for (StaffSummaryRow r : rows) {
            if ("Red".equalsIgnoreCase(r.getReadiness())) red++;
            else if ("Amber".equalsIgnoreCase(r.getReadiness())) amber++;
            else if ("Green".equalsIgnoreCase(r.getReadiness())) green++;
            else notAssessed++;
        }
        Map<String, Integer> map = new HashMap<>();
        map.put("red", red);
        map.put("amber", amber);
        map.put("green", green);
        map.put("notAssessed", notAssessed);
        map.put("total", rows.size());
        return map;
    }

    /**
     * Dashboard for a supervisor showing direct reports, team readiness, and top gaps.
     */
    @Transactional(readOnly = true)
    public DashboardSummary getSupervisorDashboard(Employee supervisor) {
        List<Employee> members = employees.findByReportingOfficerAndActiveTrue(supervisor.getEmpId());
        return buildDashboardForEmployees(members);
    }

    /**
     * Dashboard for an HoD showing departmental workforce readiness and role family breakdown.
     */
    @Transactional(readOnly = true)
    public DashboardSummary getDepartmentDashboard(String department) {
        List<Employee> deptMembers = employees.findByDepartmentAndActiveTrue(department).stream()
                .filter(e -> e.getRoleFamilyCode() != null)
                .collect(Collectors.toList());
        DashboardSummary summary = buildDashboardForEmployees(deptMembers);

        // Breakdown by Role Family
        Map<String, List<Employee>> byFamily = deptMembers.stream()
                .collect(Collectors.groupingBy(e -> e.getRoleFamilyCode() != null ? e.getRoleFamilyCode() : "–"));

        List<DashboardSummary.BreakdownRow> breakdowns = new ArrayList<>();
        Map<Long, ProfileSummary> sumMap = summaries.findByEmployeeIdIn(
                deptMembers.stream().map(Employee::getId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProfileSummary::getEmployeeId, s -> s));

        for (Map.Entry<String, List<Employee>> entry : byFamily.entrySet()) {
            String code = entry.getKey();
            List<Employee> grp = entry.getValue();
            int red = 0, amber = 0, green = 0, assessed = 0;
            double totalIdx = 0;
            int idxCount = 0;
            for (Employee e : grp) {
                ProfileSummary s = sumMap.get(e.getId());
                if (s != null) {
                    if ("Red".equalsIgnoreCase(s.getReadiness())) red++;
                    else if ("Amber".equalsIgnoreCase(s.getReadiness())) amber++;
                    else if ("Green".equalsIgnoreCase(s.getReadiness())) green++;
                    if (!"Not assessed".equalsIgnoreCase(s.getReadiness())) assessed++;
                    if (s.getIndexPct() != null) {
                        totalIdx += s.getIndexPct();
                        idxCount++;
                    }
                }
            }
            Double avgIdx = idxCount > 0 ? Math.round(10.0 * totalIdx / idxCount) / 10.0 : null;
            breakdowns.add(new DashboardSummary.BreakdownRow(code, code, grp.size(), assessed, red, amber, green, avgIdx));
        }
        breakdowns.sort(Comparator.comparing(DashboardSummary.BreakdownRow::getKey));
        summary.setBreakdowns(breakdowns);

        return summary;
    }

    /**
     * Executive organisation-wide dashboard for Management and Assessment Cell.
     */
    @Transactional(readOnly = true)
    public DashboardSummary getOrgDashboard() {
        List<Employee> allStaff = employees.findByActiveTrue().stream()
                .filter(e -> e.getRoleFamilyCode() != null)
                .collect(Collectors.toList());
        DashboardSummary summary = buildDashboardForEmployees(allStaff);

        // Breakdown by Department
        Map<String, List<Employee>> byDept = allStaff.stream()
                .collect(Collectors.groupingBy(e -> e.getDepartment() != null && !e.getDepartment().isBlank() ? e.getDepartment() : "General"));

        List<DashboardSummary.BreakdownRow> breakdowns = new ArrayList<>();
        Map<Long, ProfileSummary> sumMap = summaries.findByEmployeeIdIn(
                allStaff.stream().map(Employee::getId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProfileSummary::getEmployeeId, s -> s));

        for (Map.Entry<String, List<Employee>> entry : byDept.entrySet()) {
            String deptName = entry.getKey();
            List<Employee> grp = entry.getValue();
            int red = 0, amber = 0, green = 0, assessed = 0;
            double totalIdx = 0;
            int idxCount = 0;
            for (Employee e : grp) {
                ProfileSummary s = sumMap.get(e.getId());
                if (s != null) {
                    if ("Red".equalsIgnoreCase(s.getReadiness())) red++;
                    else if ("Amber".equalsIgnoreCase(s.getReadiness())) amber++;
                    else if ("Green".equalsIgnoreCase(s.getReadiness())) green++;
                    if (!"Not assessed".equalsIgnoreCase(s.getReadiness())) assessed++;
                    if (s.getIndexPct() != null) {
                        totalIdx += s.getIndexPct();
                        idxCount++;
                    }
                }
            }
            Double avgIdx = idxCount > 0 ? Math.round(10.0 * totalIdx / idxCount) / 10.0 : null;
            breakdowns.add(new DashboardSummary.BreakdownRow(deptName, deptName, grp.size(), assessed, red, amber, green, avgIdx));
        }
        breakdowns.sort(Comparator.comparing(DashboardSummary.BreakdownRow::getKey));
        summary.setBreakdowns(breakdowns);

        return summary;
    }

    /**
     * Generates downloadable Excel (.xlsx) report of staff assessment results.
     */
    @Transactional(readOnly = true)
    public byte[] exportStaffResults(Employee viewer, StaffFilter filter) throws IOException {
        List<StaffSummaryRow> rows = getFilteredSummaryRows(viewer, filter);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Staff Results");

            String[] cols = {
                "Employee ID", "Name (English)", "Name (Marathi)", "Department", "Location",
                "Designation", "Role Family", "Grade", "Reporting Officer",
                "Readiness", "Competency Met %", "Total Gaps", "Safety Gates"
            };

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int i = 0; i < cols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }
            sheet.createFreezePane(0, 1);

            int rowIdx = 1;
            for (StaffSummaryRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getEmpId());
                row.createCell(1).setCellValue(r.getNameEn());
                row.createCell(2).setCellValue(r.getNameMr() != null ? r.getNameMr() : "");
                row.createCell(3).setCellValue(r.getDepartment() != null ? r.getDepartment() : "");
                row.createCell(4).setCellValue(r.getLocation() != null ? r.getLocation() : "");
                row.createCell(5).setCellValue(r.getDesignation() != null ? r.getDesignation() : "");
                row.createCell(6).setCellValue(r.getRoleFamilyCode() != null ? r.getRoleFamilyCode() : "");
                row.createCell(7).setCellValue(r.getGradeCode() != null ? r.getGradeCode() : "");
                row.createCell(8).setCellValue(r.getReportingOfficer() != null ? r.getReportingOfficer() : "");
                row.createCell(9).setCellValue(r.getReadiness() != null ? r.getReadiness() : "Not assessed");
                if (r.getIndexPct() != null) {
                    row.createCell(10).setCellValue(r.getIndexPct());
                } else {
                    row.createCell(10).setCellValue("-");
                }
                row.createCell(11).setCellValue(r.getGapsCount());
                row.createCell(12).setCellValue(r.getSafetyGates());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private List<StaffSummaryRow> getFilteredSummaryRows(Employee viewer, StaffFilter filter) {
        List<Employee> base;
        if (access.hasAny(viewer.getId(), "org.view")) {
            base = employees.findByActiveTrue();
        } else if (access.hasAny(viewer.getId(), "department.view")) {
            base = employees.findByDepartmentAndActiveTrue(viewer.getDepartment());
        } else if (access.hasAny(viewer.getId(), "team.view")) {
            base = employees.findByReportingOfficerAndActiveTrue(viewer.getEmpId());
        } else {
            base = Collections.singletonList(viewer);
        }

        // Apply filters
        String q = filter.getQ().toLowerCase(Locale.ROOT);
        String dept = filter.getDepartment();
        String rf = filter.getRoleFamilyCode();
        String gr = filter.getGradeCode();
        String ro = filter.getReportingOfficer();

        List<Employee> filtered = base.stream()
                .filter(e -> q.isEmpty() || e.getEmpId().toLowerCase(Locale.ROOT).contains(q)
                        || e.getNameEn().toLowerCase(Locale.ROOT).contains(q)
                        || e.getDesignation().toLowerCase(Locale.ROOT).contains(q))
                .filter(e -> dept.isEmpty() || dept.equalsIgnoreCase(e.getDepartment()))
                .filter(e -> rf.isEmpty() || rf.equalsIgnoreCase(e.getRoleFamilyCode()))
                .filter(e -> gr.isEmpty() || gr.equalsIgnoreCase(e.getGradeCode()))
                .filter(e -> ro.isEmpty() || ro.equalsIgnoreCase(e.getReportingOfficer()))
                .sorted(Comparator.comparing(Employee::getEmpId))
                .collect(Collectors.toList());

        Map<Long, ProfileSummary> sumMap = summaries.findByEmployeeIdIn(
                filtered.stream().map(Employee::getId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProfileSummary::getEmployeeId, s -> s));

        String readinessFilter = filter.getReadiness();
        List<StaffSummaryRow> rows = new ArrayList<>();
        for (Employee e : filtered) {
            ProfileSummary s = sumMap.get(e.getId());
            StaffSummaryRow row = new StaffSummaryRow(e, s);
            if (readinessFilter.isEmpty() || readinessFilter.equalsIgnoreCase(row.getReadiness())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private DashboardSummary buildDashboardForEmployees(List<Employee> emps) {
        DashboardSummary sum = new DashboardSummary();
        sum.setTotalPeople(emps.size());

        if (emps.isEmpty()) {
            return sum;
        }

        Map<Long, ProfileSummary> sumMap = summaries.findByEmployeeIdIn(
                emps.stream().map(Employee::getId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProfileSummary::getEmployeeId, s -> s));

        int red = 0, amber = 0, green = 0, notAssessed = 0, assessed = 0, gates = 0;
        double indexSum = 0;
        int indexCount = 0;

        for (Employee e : emps) {
            ProfileSummary s = sumMap.get(e.getId());
            if (s != null) {
                if ("Red".equalsIgnoreCase(s.getReadiness())) red++;
                else if ("Amber".equalsIgnoreCase(s.getReadiness())) amber++;
                else if ("Green".equalsIgnoreCase(s.getReadiness())) green++;
                else notAssessed++;

                if (!"Not assessed".equalsIgnoreCase(s.getReadiness())) {
                    assessed++;
                }
                gates += s.getSafetyGates();
                if (s.getIndexPct() != null) {
                    indexSum += s.getIndexPct();
                    indexCount++;
                }
            } else {
                notAssessed++;
            }
        }

        sum.setAssessedPeople(assessed);
        sum.setCoveragePct(emps.isEmpty() ? 0.0 : Math.round(1000.0 * assessed / emps.size()) / 10.0);
        sum.setRedCount(red);
        sum.setAmberCount(amber);
        sum.setGreenCount(green);
        sum.setNotAssessedCount(notAssessed);
        sum.setSafetyGates(gates);
        sum.setAverageIndexPct(indexCount > 0 ? Math.round(10.0 * indexSum / indexCount) / 10.0 : null);

        // Top Gaps
        Map<String, Integer> gapCounts = new HashMap<>();
        Map<String, ProfileRow> metaMap = new HashMap<>();
        Map<Long, Profile> profileMap = profileService.buildAll(emps);

        for (Profile p : profileMap.values()) {
            for (ProfileRow r : p.getRows()) {
                if (r.isAssessed() && r.getGap() > 0) {
                    gapCounts.put(r.getCode(), gapCounts.getOrDefault(r.getCode(), 0) + 1);
                    metaMap.putIfAbsent(r.getCode(), r);
                }
            }
        }

        List<DashboardSummary.TopGapDTO> top = gapCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    ProfileRow r = metaMap.get(entry.getKey());
                    return new DashboardSummary.TopGapDTO(entry.getKey(), r.getName(), r.getCategory(), r.isSafetyCritical(), entry.getValue());
                })
                .collect(Collectors.toList());


        sum.setTopGaps(top);
        return sum;
    }
}

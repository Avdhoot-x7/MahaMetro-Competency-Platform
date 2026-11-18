package in.mahametro.competency.importers;

import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.employees.DesignationMap;
import in.mahametro.competency.employees.DesignationMapRepository;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.employees.Grade;
import in.mahametro.competency.employees.GradeRepository;
import in.mahametro.competency.employees.RoleFamily;
import in.mahametro.competency.employees.RoleFamilyRepository;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports and validates employee master data from CSV or Excel files.
 * Supports dry-run validation, designation mapping fallbacks, and supervisor role elevation.
 */
@Service
public class EmployeeImportService {

    public static final String DEFAULT_PASSWORD = "Demo@2027";

    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
        Map.entry("emp_id", List.of("employeeid", "empid", "personnelnumber", "persno", "pernr", "employeeno", "empcode")),
        Map.entry("name_en", List.of("name", "employeename", "nameenglish", "fullname")),
        Map.entry("name_mr", List.of("namemarathi", "marathiname", "namemr")),
        Map.entry("designation", List.of("designation", "position", "positiontext", "job")),
        Map.entry("department", List.of("department", "orgunit", "organizationalunit", "dept")),
        Map.entry("location", List.of("location", "personnelarea", "personnelsubarea", "worklocation")),
        Map.entry("grade", List.of("grade", "gradeband", "paylevel", "employeesubgroup")),
        Map.entry("role_family", List.of("rolefamily", "rolefamilycode")),
        Map.entry("reporting_officer", List.of("reportingofficer", "reportingofficerid", "supervisorid", "managerid", "ro")),
        Map.entry("email", List.of("email", "emailid", "officialemail")),
        Map.entry("mobile", List.of("mobile", "mobileno", "phone")),
        Map.entry("extra_time_pct", List.of("extratime", "extratimepct")),
        Map.entry("active", List.of("active", "status", "employmentstatus"))
    );

    private static final Set<String> INACTIVE_TOKENS = Set.of("0", "n", "no", "inactive", "withdrawn", "left", "false");

    private final EmployeeRepository employees;
    private final EmployeeRoleRepository employeeRoles;
    private final RoleFamilyRepository roleFamilies;
    private final GradeRepository grades;
    private final DesignationMapRepository designationMaps;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final DataFormatter dataFormatter = new DataFormatter();

    public EmployeeImportService(EmployeeRepository employees, EmployeeRoleRepository employeeRoles,
            RoleFamilyRepository roleFamilies, GradeRepository grades, DesignationMapRepository designationMaps,
            PasswordEncoder passwordEncoder, AuditService audit) {
        this.employees = employees;
        this.employeeRoles = employeeRoles;
        this.roleFamilies = roleFamilies;
        this.grades = grades;
        this.designationMaps = designationMaps;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    /**
     * Parses and processes employee master file (CSV or Excel).
     * In dry-run mode, no database modifications or audit records are committed.
     */
    @Transactional
    public EmployeeImportReport importEmployees(InputStream inputStream, String filename, Employee actor, boolean dryRun)
            throws Exception {
        EmployeeImportReport report = new EmployeeImportReport(dryRun);
        List<Map<String, String>> rows = readRows(inputStream, filename, report);
        if (rows.isEmpty()) {
            report.addError("File contains no data rows.");
            return report;
        }

        Set<String> knownFamilies = roleFamilies.findAll().stream()
                .map(RoleFamily::getCode)
                .collect(Collectors.toSet());
        Set<String> knownGrades = grades.findAll().stream()
                .map(Grade::getCode)
                .collect(Collectors.toSet());
        Map<String, DesignationMap> dmap = designationMaps.findAll().stream()
                .collect(Collectors.toMap(d -> d.getDesignation().toLowerCase(Locale.ROOT), d -> d, (a, b) -> a));

        String defaultHash = passwordEncoder.encode(DEFAULT_PASSWORD);
        Set<String> allReportingOfficers = new HashSet<>();

        int rowNumber = 1; // row 1 is header
        for (Map<String, String> row : rows) {
            rowNumber++;
            String empId = pick(row, "emp_id");
            String nameEn = pick(row, "name_en");

            if (empId.isBlank() || nameEn.isBlank()) {
                report.incrementSkipped();
                report.addError("Row " + rowNumber + ": employee ID and name are required");
                continue;
            }

            Optional<Employee> existingOpt = employees.findByEmpId(empId);
            boolean isNew = existingOpt.isEmpty();
            Employee emp = isNew ? new Employee(empId, nameEn) : existingOpt.get();

            emp.setNameEn(nameEn);
            String nameMr = pick(row, "name_mr");
            if (!nameMr.isBlank()) emp.setNameMr(nameMr);

            String designation = pick(row, "designation");
            if (!designation.isBlank()) emp.setDesignation(designation);

            String department = pick(row, "department");
            if (!department.isBlank()) emp.setDepartment(department);

            String location = pick(row, "location");
            if (!location.isBlank()) emp.setLocation(location);

            String email = pick(row, "email");
            if (!email.isBlank()) emp.setEmail(email);

            String mobile = pick(row, "mobile");
            if (!mobile.isBlank()) emp.setMobile(mobile);

            String reportingOfficer = pick(row, "reporting_officer");
            if (!reportingOfficer.isBlank()) {
                emp.setReportingOfficer(reportingOfficer);
                allReportingOfficers.add(reportingOfficer);
            }

            // Role family resolution: input column -> designation map -> warning
            String rfInput = pick(row, "role_family").toUpperCase(Locale.ROOT);
            DesignationMap dm = !emp.getDesignation().isBlank()
                    ? dmap.get(emp.getDesignation().toLowerCase(Locale.ROOT))
                    : null;

            if (knownFamilies.contains(rfInput)) {
                emp.setRoleFamilyCode(rfInput);
            } else if (dm != null && dm.getRoleFamilyCode() != null && knownFamilies.contains(dm.getRoleFamilyCode())) {
                emp.setRoleFamilyCode(dm.getRoleFamilyCode());
            } else if (isNew || emp.getRoleFamilyCode() == null || emp.getRoleFamilyCode().isBlank()) {
                report.addWarning("Row " + rowNumber + " (" + emp.getEmpId() + "): designation '"
                        + emp.getDesignation() + "' not mapped to a role family");
            }

            // Grade resolution: input column -> designation map -> warning
            String grInput = pick(row, "grade").toUpperCase(Locale.ROOT);
            if (knownGrades.contains(grInput)) {
                emp.setGradeCode(grInput);
            } else if (dm != null && dm.getGradeCode() != null && knownGrades.contains(dm.getGradeCode())) {
                emp.setGradeCode(dm.getGradeCode());
            } else if (isNew || emp.getGradeCode() == null || emp.getGradeCode().isBlank()) {
                report.addWarning("Row " + rowNumber + " (" + emp.getEmpId() + "): grade '"
                        + grInput + "' not recognised");
            }

            // Extra time percentage
            String extraTimeStr = pick(row, "extra_time_pct");
            if (!extraTimeStr.isBlank()) {
                try {
                    String clean = extraTimeStr.contains(".") ? extraTimeStr.substring(0, extraTimeStr.indexOf('.')) : extraTimeStr;
                    clean = clean.replaceAll("[^0-9]", "");
                    if (!clean.isEmpty()) {
                        emp.setExtraTimePct(Integer.parseInt(clean));
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // Active status
            String activeStr = pick(row, "active");
            if (!activeStr.isBlank()) {
                emp.setActive(!INACTIVE_TOKENS.contains(activeStr.trim().toLowerCase(Locale.ROOT)));
            }

            if (isNew) {
                report.incrementCreated();
                if (!dryRun) {
                    emp.setSource("sap_hr");
                    if (emp.getPasswordHash() == null || emp.getPasswordHash().isBlank()) {
                        emp.setPasswordHash(defaultHash);
                    }
                    emp = employees.save(emp);
                    // Ensure employee role
                    if (!employeeRoles.existsByEmployeeIdAndRoleCodeAndScopeTypeAndScopeValue(emp.getId(), "employee", "ALL", "")) {
                        employeeRoles.save(new EmployeeRole(emp.getId(), "employee", "ALL", "", actor != null ? actor.getEmpId() : "system"));
                    }
                }
            } else {
                report.incrementUpdated();
                if (!dryRun) {
                    employees.save(emp);
                }
            }
        }

        if (!dryRun) {
            // Elevated supervisors: anyone who is somebody's reporting officer receives supervisor role
            List<String> roInDb = employees.findDistinctReportingOfficers();
            allReportingOfficers.addAll(roInDb);

            for (String roId : allReportingOfficers) {
                employees.findByEmpId(roId).ifPresent(roEmp -> {
                    if (!employeeRoles.existsByEmployeeIdAndRoleCodeAndScopeTypeAndScopeValue(roEmp.getId(), "supervisor", "ALL", "")) {
                        employeeRoles.save(new EmployeeRole(roEmp.getId(), "supervisor", "ALL", "", actor != null ? actor.getEmpId() : "system"));
                    }
                });
            }

            if (actor != null) {
                audit.record(actor.getEmpId(), "employees_imported", "employee", "",
                        "created=" + report.getCreated() + ", updated=" + report.getUpdated() + ", skipped=" + report.getSkipped());
            }
        }

        return report;
    }

    /**
     * Generates downloadable Excel template for bulk employee import.
     */
    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet ws = wb.createSheet("Employees");

            String[] cols = {
                "Employee ID", "Name", "Name (Marathi)", "Designation", "Department",
                "Location", "Grade", "Role family", "Reporting Officer ID",
                "Email", "Mobile", "Extra time %", "Active"
            };

            Row headerRow = ws.createRow(0);
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int i = 0; i < cols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                ws.setColumnWidth(i, 20 * 256);
            }
            ws.createFreezePane(0, 1);

            // Sample row
            Row sampleRow = ws.createRow(1);
            String[] sampleData = {
                "100234", "A. B. Patil", "अ. ब. पाटील", "Station Controller", "Operations",
                "Pune – Line 1", "G2", "SC", "100120", "ab.patil@mahametro.org",
                "9876543210", "0", "Y"
            };
            for (int i = 0; i < sampleData.length; i++) {
                sampleRow.createCell(i).setCellValue(sampleData[i]);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private List<Map<String, String>> readRows(InputStream in, String filename, EmployeeImportReport report) throws Exception {
        String lower = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || lower.endsWith(".xls")) {
            return readExcelRows(in);
        } else {
            return readCsvRows(in);
        }
    }

    private List<Map<String, String>> readExcelRows(InputStream in) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheet("Employees") != null ? wb.getSheet("Employees") : wb.getSheetAt(0);
            List<String> headers = null;

            for (Row r : sheet) {
                if (headers == null) {
                    int nonBlank = 0;
                    for (Cell c : r) {
                        if (!dataFormatter.formatCellValue(c).trim().isEmpty()) {
                            nonBlank++;
                        }
                    }
                    if (nonBlank >= 2) {
                        headers = new ArrayList<>();
                        for (int i = 0; i < r.getLastCellNum(); i++) {
                            Cell c = r.getCell(i);
                            headers.add(normalize(dataFormatter.formatCellValue(c)));
                        }
                    }
                    continue;
                }

                Map<String, String> rowMap = new HashMap<>();
                boolean anyData = false;
                for (int i = 0; i < headers.size(); i++) {
                    String val = dataFormatter.formatCellValue(r.getCell(i)).trim();
                    if (!val.isEmpty()) {
                        anyData = true;
                    }
                    String headerKey = headers.get(i);
                    if (!headerKey.isEmpty()) {
                        rowMap.put(headerKey, val);
                    }
                }
                if (anyData) {
                    rows.add(rowMap);
                }
            }
        }
        return rows;
    }

    private List<Map<String, String>> readCsvRows(InputStream in) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        List<String> headers = null;
        char delimiter = ',';

        while ((line = reader.readLine()) != null) {
            // Strip BOM if present on first line
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (headers == null) {
                delimiter = detectDelimiter(line);
                List<String> rawHeaders = parseCsvLine(line, delimiter);
                if (rawHeaders.size() >= 2) {
                    headers = rawHeaders.stream()
                            .map(EmployeeImportService::normalize)
                            .collect(Collectors.toList());
                }
                continue;
            }

            List<String> values = parseCsvLine(line, delimiter);
            Map<String, String> rowMap = new HashMap<>();
            boolean anyData = false;
            for (int i = 0; i < headers.size() && i < values.size(); i++) {
                String val = values.get(i).trim();
                if (!val.isEmpty()) {
                    anyData = true;
                }
                String headerKey = headers.get(i);
                if (!headerKey.isEmpty()) {
                    rowMap.put(headerKey, val);
                }
            }
            if (anyData) {
                rows.add(rowMap);
            }
        }
        return rows;
    }

    private char detectDelimiter(String line) {
        int commas = countOccurrences(line, ',');
        int semicolons = countOccurrences(line, ';');
        int tabs = countOccurrences(line, '\t');
        int pipes = countOccurrences(line, '|');

        if (semicolons > commas && semicolons >= tabs && semicolons >= pipes) return ';';
        if (tabs > commas && tabs >= semicolons && tabs >= pipes) return '\t';
        if (pipes > commas && pipes >= semicolons && pipes >= tabs) return '|';
        return ',';
    }

    private int countOccurrences(String str, char ch) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ch) count++;
        }
        return count;
    }

    public static List<String> parseCsvLine(String line, char delimiter) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++; // skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String pick(Map<String, String> row, String canonicalKey) {
        List<String> aliases = ALIASES.get(canonicalKey);
        if (aliases == null) return "";
        for (String alias : aliases) {
            String val = row.get(alias);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return "";
    }
}

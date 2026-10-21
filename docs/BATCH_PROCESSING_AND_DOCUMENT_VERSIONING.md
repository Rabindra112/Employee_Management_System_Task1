# Batch Processing and Document Versioning

This document describes the employee CSV import and employee document workflows implemented in this project. Employee import uses Spring Batch. Document uploads are stored as individual files, with metadata and version history in the database.

## Employee CSV batch import

Only an Admin can use the bulk import controls and endpoints. The import feature is enabled by default and can be disabled with `app.employee-import.enabled=false`.

### 1. Prepare the CSV file

1. Sign in as an Admin and open **Employee Bulk Import** on the Admin Dashboard.
2. Select **Download Template**. The template contains the required header:

   ```csv
   name,department,salary
   ```

3. Add one employee per row, preserving that column order. Name and department must satisfy the employee request validation rules; salary must be numeric and greater than zero.
4. Save as a UTF-8 `.csv` file. Quoted comma-containing fields and doubled quotes inside quoted fields are supported.

The reader checks the header names and order, ignoring case and surrounding whitespace. Rows must contain exactly three fields. The processor validates each row and rejects duplicate name/department/salary combinations within the same upload (case-insensitive for name and department). It does not check for duplicates against employees already in the database.

### 2. Upload, start, and monitor

1. Choose the CSV file and select **Upload and import**. The file is saved under the configured employee-import storage directory (default `./employee-imports`).
2. The frontend starts a batch job for that upload and polls its status while it is `STARTING` or `STARTED`.
3. The job reads and writes records in chunks of 100. Valid rows create employee records. Invalid rows are recorded with their CSV row number, source text, and validation error; processing continues for other rows.
4. Review the job's status and **Imported** and **Errors** counts in the import table. Use **Refresh** to reload job history. The history endpoint returns executions for up to the latest 100 job instances.
5. If errors are reported, use the row's **Download CSV** action to save the error report. Correct the input and upload it again as a new import. Each uploaded file can be launched only once.

If a job reports `FAILED`, the UI message suggests checking the header and format. A failed job may have already committed earlier chunks; inspect the imported count and error report before retrying to avoid creating duplicate records. A validation error on a row is recorded as a row error; it does not by itself fail the whole job.

The bulk import section also provides **Download CSV** and **Download Excel** to export employee records; these are exports, not batch import inputs.

## Employee document versioning

Employee-owned uploads are available to linked Employee accounts. Admins manage document types and review pending versions. The document storage directory is controlled by `DOCUMENT_STORAGE_PATH` and defaults to `./document-storage`.

### 1. Upload the first version (Employee)

1. Sign in to the Employee Dashboard. The Employee account must be linked to an employee record by an Admin.
2. Under **My Documents**, select a document type and choose a file with a `.pdf`, `.docx`, `.jpg`, `.jpeg`, or `.png` extension.
3. Select **Upload**. Files must be no larger than 10 MB, and the extension, reported content type, and initial file signature must match an allowed type.
4. The app creates a document and version 1 with status `PENDING`. Its file is stored using an application-generated name; the original filename is retained in version metadata.

### 2. Review a pending version (Admin)

1. Sign in to the Admin Dashboard and open **Document Approvals**.
2. Review the pending entry. **View / Download** retrieves the submitted version; **Version History** lists versions for that document.
3. Select **Approve**, or enter a rejection reason and select **Reject**. A version can be reviewed only while pending, and an Admin cannot review their own upload.
4. Approval records the reviewer and approval time and updates the document's latest approved version when appropriate. Rejection requires a reason and records the reviewer, time, and reason.

### 3. View history, download, or resubmit (Employee)

1. Under **My Documents**, select **Version History** to see version numbers, filenames, and statuses.
2. Download an approved version using **Download latest approved** or its version-specific **Download** action. Employees cannot download a pending or rejected version.
3. If the current version is `REJECTED`, choose a replacement file and select **Resubmit**. The app adds the next version number to the same document and sets it to `PENDING` for review.
4. Previously approved versions remain in history and remain downloadable even when a later version is pending or rejected.

The employee interface exposes resubmission after rejection. It does not provide an employee action to create an arbitrary new version after an approved version. The Admin API includes a separate version-upload operation for admin-managed documents.

### Storage and transaction behavior

Version files are kept separately from database rows. The app writes a generated filename under the configured storage directory and stores that key in the version record. If the database transaction rolls back after the file is written, a transaction callback attempts to delete the new file. Back up the database and document-storage directory together so their metadata and file contents stay aligned.

## Relevant implementation files

- Batch job configuration and 100-row chunk size: `backend/src/main/java/com/example/ems/batch/EmployeeImportBatchConfig.java`
- Import upload, launch, status, history, and error-report endpoints: `backend/src/main/java/com/example/ems/batch/EmployeeImportController.java`
- CSV header reader, parser, processor, and writer: `backend/src/main/java/com/example/ems/batch/EmployeeCsvItemReader.java`, `EmployeeCsvParser.java`, `EmployeeImportProcessor.java`, and `EmployeeImportWriter.java`
- Batch import interface: `frontend/src/components/EmployeeBulkImport.jsx`
- Document validation, storage, versioning, and review rules: `backend/src/main/java/com/example/ems/service/DocumentService.java`
- Document API routes and authorization-sensitive operations: `backend/src/main/java/com/example/ems/controller/DocumentController.java`
- Employee document and Admin review interfaces: `frontend/src/components/MyDocuments.jsx` and `frontend/src/components/DocumentApprovals.jsx`

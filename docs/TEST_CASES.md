# Employee Management System - Test Cases

| ID | Test Scenario | Expected Result |
|----|---------------|-----------------|
| TC01 | Add valid employee | Employee created successfully |
| TC02 | Add employee with empty name | HTTP 400 |
| TC03 | Add employee with negative salary | HTTP 400 |
| TC04 | Retrieve all employees | HTTP 200 |
| TC05 | Retrieve existing employee by ID | HTTP 200 |
| TC06 | Retrieve nonexistent employee | HTTP 404 |
| TC07 | Update existing employee | Employee updated |
| TC08 | Delete existing employee | HTTP 204 |
| TC09 | Delete nonexistent employee | HTTP 404 |
| TC10 | Search by employee name | Matching employees returned |
| TC11 | Filter by department | Filtered employees returned |
| TC12 | Request first page | Paginated results returned |
| TC13 | Request invalid page size | HTTP 400 |
| TC14 | Retrieve dashboard statistics | Statistics returned |
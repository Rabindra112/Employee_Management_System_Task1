# Employee Management System

## Developed By
Rabindra Kumar Mahato

## 1. Project Overview

Employee Management System is a full-stack web application
developed using Spring Boot, React, and MySQL.

The application allows users to manage employee records
through a user-friendly interface.

It supports CRUD operations, searching, filtering,
pagination, and dashboard statistics.

## 2. Technology Stack

### Backend
- Java 21
- Spring Boot
- Spring Data JPA
- Hibernate
- Jakarta Bean Validation
- Lombok
- Maven

### Frontend
- React 19
- Vite
- JavaScript
- Axios
- CSS

### Database
- MySQL

## 3. Features

- Add Employee
- View Employees
- Update Employee
- Delete Employee
- Search Employees by Name
- Filter Employees by Department
- Server-Side Pagination
- Dashboard Statistics
- Input Validation
- Global Exception Handling
- Application Logging

## 4. System Architecture

The application follows a three-tier architecture:

React Frontend
->
Spring Boot REST API
->
EmployeeController
->
EmployeeService
->
EmployeeRepository
->
MySQL Database

## 5. Database Setup

Create the database:

```sql
CREATE DATABASE IF NOT EXISTS employee_management;
```

Configure the MySQL connection in:

backend/src/main/resources/application.properties

Example:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/employee_management
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update

server.port=8080

app.frontend-origin=http://localhost:5174
```

Set DB_PASSWORD to your local MySQL password.

## 6. How to Run the Backend

Requirements:
- Java 21
- Maven
- MySQL

Steps:

1. Start MySQL.
2. Create the employee_management database.
3. Configure database credentials.
4. Open the backend folder.
5. Run:

```bash
mvn spring-boot:run
```

Backend URL:

http://localhost:8080

## 7. How to Run the Frontend

Requirements:
- Node.js
- npm

Open the frontend folder.

Install dependencies:

```bash
npm install
```

Start the application:

```bash
npm run dev
```

Frontend URL:

http://localhost:5174

Ensure Vite is configured to use port 5174.

## 8. REST API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/employees | Add employee |
| GET | /api/employees | Get employees |
| GET | /api/employees/{id} | Get employee by ID |
| PUT | /api/employees/{id} | Update employee |
| DELETE | /api/employees/{id} | Delete employee |
| GET | /api/employees/stats | Dashboard statistics |

## 9. Validation

The backend validates:

- Employee name cannot be empty.
- Department cannot be empty.
- Salary must be greater than zero.
- Employee ID must be positive.
- Pagination parameters must be valid.

## 10. Exception Handling

The application uses centralized exception handling.

Common HTTP status codes:

- 200 OK
- 201 Created
- 204 No Content
- 400 Bad Request
- 404 Not Found
- 500 Internal Server Error

## 11. Documentation

Additional documentation is available in the docs folder:

- employee_management_HLD.pdf
- IMPLEMENTATION_AND_DESIGN.md
- TEST_CASES.md


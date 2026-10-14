# Implementation and Design

## 1. Architecture

The application follows Controller-Service-Repository
architecture.

## 2. Backend Components

### EmployeeController

Responsible for handling HTTP requests.

Endpoints:
- POST /api/employees
- GET /api/employees
- GET /api/employees/{id}
- PUT /api/employees/{id}
- DELETE /api/employees/{id}
- GET /api/employees/stats

### EmployeeService

Responsible for:
- Business logic
- Employee CRUD operations
- Dashboard calculations
- Transaction management

### EmployeeRepository

Uses Spring Data JPA for:
- Database operations
- Search queries
- Pagination

### Employee Entity

Fields:
- id
- name
- department
- salary

### EmployeeRequest DTO

Used to validate employee input data.

## 3. Frontend Components

### App.jsx

Responsible for:
- Employee list
- Dashboard
- Search
- Filtering
- Pagination
- CRUD state management

### EmployeeForm.jsx

Responsible for:
- Add employee form
- Edit employee form
- Input validation

### employees.js

Responsible for communicating with Spring Boot APIs
using Axios.

## 4. Database Design

Database: employee_management

Table: employees

Columns:
- id BIGINT PRIMARY KEY
- name VARCHAR(100)
- department VARCHAR(80)
- salary DECIMAL(12,2)

## 5. Request Flow

React
->
Axios
->
EmployeeController
->
EmployeeService
->
EmployeeRepository
->
MySQL

The response follows the reverse path.

## 6. Exception Handling

GlobalExceptionHandler handles application errors
and returns meaningful HTTP responses.

## 7. Transaction Management

@Transactional is used for database write operations.

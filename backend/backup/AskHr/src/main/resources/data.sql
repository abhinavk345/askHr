INSERT INTO employee_auth (
    employee_id,
    username,
    password,
    role,
    enabled
)
VALUES (
    '273273',
    'abhinav.kumar@company.com',
    '273273',
    'EMPLOYEE',
    true
);

INSERT INTO employee (
    employee_id,
    full_name,
    email,
    department,
    designation,
    manager_employee_id,
    status,
    date_of_joining
)
VALUES (
    '273273',
    'Abhinav Kumar',
    'abhinav.kumar@company.com',
    'Engineering',
    'Senior Developer',
    '272272',
    'ACTIVE',
    NOW()
);

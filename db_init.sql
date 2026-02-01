CREATE TABLE IF NOT EXISTS departments (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    user_id        BIGINT PRIMARY KEY,
    username       VARCHAR(100),
    full_name      VARCHAR(255),
    role           VARCHAR(20) DEFAULT 'new',
    department_id  INTEGER REFERENCES departments(id),
    is_on_vacation BOOLEAN DEFAULT FALSE,
    status         VARCHAR(20) DEFAULT 'pending',
    is_admin       BOOLEAN DEFAULT FALSE,
    is_supervisor  BOOLEAN DEFAULT FALSE
);

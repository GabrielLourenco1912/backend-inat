insert into roles (code, name, description)
values ('ADMIN', 'Administrator', 'Full platform administration'),
       ('INSTRUCTOR', 'Instructor', 'Assigned lesson management'),
       ('LEARNER', 'Learner', 'Own learning experience'),
       ('EMPLOYER_MANAGER', 'Employer manager', 'Scoped access to an employer organization');

insert into person_types (code)
values ('ADMIN'),
       ('EMPLOYER_MANAGER'),
       ('GUARDIAN'),
       ('INSTRUCTOR'),
       ('LEARNER');

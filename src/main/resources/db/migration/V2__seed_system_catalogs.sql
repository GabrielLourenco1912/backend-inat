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

insert into organization_types (code)
values ('EMPLOYER'),
       ('SCHOOL');

insert into addresses (country_code, state_code, created_at, postal_code, updated_at, street_number, id, address_line_2, city, district, street)
values
       ('BR', 'PR', NOW(6), '83252000', NOW(6), '1257', '1', 'CASA DO GABE', 'PARANAGUÁ', 'VILA BELA - ILHA DOS VALADARES', 'RUA JOÃO TEIXEIRA'),
       ('BR', 'PR', NOW(6), '00000000', NOW(6), '0000', '01K0000000MNZ8WXSCRRPWYDBZ', NULL, 'PARANAGUÁ', 'VILA BELA - ILHA DOS VALADARES', 'RUA JOÃO TEIXEIRA');

insert into people (birth_date, created_at, updated_at, tax_id, phone_number, address_id, id, gender, full_name, contact_email, status)
values
       ('2005-01-25', NOW(6), NOW(6), '09286696965', '41984359163', '1', '1', 'Homem', 'Gabriel Gomes Lourenço', 'gabri02gomes@hotmail.com', 'ACTIVE'),
       ('1990-03-12', NOW(6), NOW(6), '00000000000', '00000000000', '01K0000000MNZ8WXSCRRPWYDBZ', '01K0000000CKCFKK7S3JGYA3QK', 'Homem', 'Marciney', 'marciney@inat.org.br', 'ACTIVE');


insert into person_type_assignments (person_id, person_type_code)
values
       ('1', 'ADMIN'),
       ('1', 'LEARNER'),
       ('01K0000000CKCFKK7S3JGYA3QK', 'ADMIN');

insert into users (
       failed_authentication_attempts,
       regulation_accepted,
       created_at,
       email_verified_at,
       last_login_at,
       locked_until,
       regulation_accepted_at,
       token_version,
       updated_at,
       id,
       person_id,
       display_name,
       login_email,
       password_hash,
       status,
       status_before_lock
)
values
       (0, 1, NOW(6), NOW(6), NULL, NULL, NOW(6), 0, NOW(6),
        '1', '1', 'gabrielgomes', 'gabri02gomes@hotmail.com',
        '$2a$10$X2EwnSG.swScwOdvm3bDTOtlZ.9.QtCQaDp2KhoM/.o4PZg1LVTb2', 'ACTIVE', NULL);

insert into user_roles (role_id, granted_at, granted_by_user_id, user_id)
select r.id, NOW(6), NULL, '1'
from roles as r
where r.code in ('ADMIN', 'LEARNER');

insert into learners (has_completed_high_school,
                      created_at,
                      updated_at,
                      id, person_id,
                      status
) values (1, NOW(6), NOW(6), 1, 1, 'ACTIVE')

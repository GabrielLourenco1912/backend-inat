    create table activities (
        max_score decimal(7,2),
        available_at datetime(6) not null,
        created_at datetime(6) not null,
        due_at datetime(6) not null,
        due_reminder_processed_at datetime(6),
        updated_at datetime(6) not null,
        created_by_user_id varchar(26) not null,
        id varchar(26) not null,
        lesson_id varchar(26) not null,
        title varchar(160) not null,
        description text not null,
        status enum ('CANCELLED','CLOSED','DRAFT','PUBLISHED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table activity_files (
        sort_order integer not null,
        created_at datetime(6) not null,
        activity_id varchar(255) not null,
        file_id varchar(255) not null,
        primary key (activity_id, file_id)
    ) engine=InnoDB;

    create table activity_submissions (
        score decimal(7,2),
        created_at datetime(6) not null,
        graded_at datetime(6),
        submitted_at datetime(6),
        updated_at datetime(6) not null,
        activity_id varchar(26) not null,
        graded_by_user_id varchar(26),
        id varchar(26) not null,
        learner_id varchar(26) not null,
        feedback text,
        text_answer text,
        status enum ('DRAFT','GRADED','LATE','RETURNED','SUBMITTED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table addresses (
        country_code varchar(2) not null,
        state_code varchar(2) not null,
        created_at datetime(6) not null,
        postal_code varchar(8) not null,
        updated_at datetime(6) not null,
        street_number varchar(20) not null,
        id varchar(26) not null,
        address_line_2 varchar(100),
        city varchar(100) not null,
        district varchar(100) not null,
        street varchar(150) not null,
        primary key (id)
    ) engine=InnoDB;

    create table attendance_records (
        check_in_at datetime(6),
        check_out_at datetime(6),
        recorded_at datetime(6) not null,
        updated_at datetime(6) not null,
        id varchar(26) not null,
        lesson_participant_id varchar(26) not null,
        recorded_by_user_id varchar(26) not null,
        notes varchar(500),
        status enum ('ABSENT','EXCUSED','LATE','PARTIAL','PRESENT') not null,
        primary key (id)
    ) engine=InnoDB;

    create table auth_challenges (
        failed_attempts integer not null,
        max_attempts integer not null,
        consumed_at datetime(6),
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        updated_at datetime(6) not null,
        version bigint not null,
        id varchar(26) not null,
        user_id varchar(26) not null,
        code_nonce varchar(32) not null,
        requested_ip_address varchar(45),
        code_digest varchar(64) not null,
        target_email varchar(254) not null,
        requested_user_agent varchar(255),
        purpose enum ('EMAIL_VERIFICATION','MFA_LOGIN','PASSWORD_RESET') not null,
        primary key (id)
    ) engine=InnoDB;

    create table cohort_enrollments (
        end_date date,
        start_date date not null,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        cohort_id varchar(26) not null,
        contract_id varchar(26) not null,
        id varchar(26) not null,
        status enum ('ACTIVE','CANCELLED','COMPLETED','PENDING') not null,
        primary key (id)
    ) engine=InnoDB;

    create table cohorts (
        default_weekday integer not null,
        end_date date,
        max_learners integer,
        start_date date not null,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        shift_code varchar(20) not null,
        id varchar(26) not null,
        code varchar(40) not null,
        name varchar(120) not null,
        status enum ('ACTIVE','CANCELLED','COMPLETED','PLANNED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table contract_documents (
        document_type_id smallint not null,
        is_current bit not null,
        version_number integer not null,
        created_at datetime(6) not null,
        contract_id varchar(26) not null,
        file_id varchar(26) not null,
        id varchar(26) not null,
        primary key (id)
    ) engine=InnoDB;

    create table contract_school_changes (
        changed_at datetime(6) not null,
        changed_by_user_id varchar(26),
        contract_id varchar(26) not null,
        id varchar(26) not null,
        new_school_id varchar(26),
        previous_school_id varchar(26),
        primary key (id)
    ) engine=InnoDB;

    create table contracts (
        end_date date,
        monthly_salary decimal(12,2) not null,
        start_date date not null,
        weekly_workload_minutes integer not null,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        employer_id varchar(26) not null,
        id varchar(26) not null,
        learner_id varchar(26) not null,
        school_id varchar(26),
        status enum ('ACTIVE','CANCELLED','DRAFT','ENDED','SUSPENDED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table contact_messages (
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        id varchar(26) not null,
        phone varchar(30),
        contact_type enum ('COMPANY','FAMILY_OR_GUARDIAN','OTHER','YOUTH_INTERESTED') not null,
        status enum ('ARCHIVED','NEW','READ') not null,
        name varchar(150) not null,
        email varchar(254) not null,
        message text not null,
        primary key (id)
    ) engine=InnoDB;

    create table document_types (
        id smallint not null auto_increment,
        code varchar(50) not null,
        name varchar(100) not null,
        description varchar(255),
        scope enum ('BOTH','CONTRACT','PERSON') not null,
        primary key (id)
    ) engine=InnoDB;

    create table files (
        created_at datetime(6) not null,
        size_bytes bigint not null,
        id varchar(26) not null,
        uploaded_by_user_id varchar(26),
        checksum_sha256 varchar(64) not null,
        mime_type varchar(150) not null,
        storage_key varchar(512) not null,
        original_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table learner_guardians (
        is_legal_guardian bit not null,
        is_primary_contact bit not null,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        relationship_type varchar(30) not null,
        guardian_person_id varchar(255) not null,
        learner_id varchar(255) not null,
        primary key (guardian_person_id, learner_id)
    ) engine=InnoDB;

    create table learners (
        has_completed_high_school bit not null,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        id varchar(26) not null,
        person_id varchar(26) not null,
        registration_number varchar(30) not null,
        status enum ('ACTIVE','INACTIVE','SUSPENDED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table lesson_participants (
        assigned_at datetime(6) not null,
        updated_at datetime(6) not null,
        assigned_by_user_id varchar(26),
        id varchar(26) not null,
        learner_id varchar(26) not null,
        lesson_id varchar(26) not null,
        source_enrollment_id varchar(26),
        assignment_reason varchar(500),
        participation_type enum ('EXTRA','MAKEUP','REGULAR','TRANSFERRED') not null,
        status enum ('CANCELLED','EXPECTED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table lessons (
        created_at datetime(6) not null,
        ends_at datetime(6) not null,
        starts_at datetime(6) not null,
        updated_at datetime(6) not null,
        cohort_id varchar(26) not null,
        id varchar(26) not null,
        instructor_person_id varchar(26) not null,
        room varchar(80),
        title varchar(160) not null,
        meeting_url varchar(512),
        external_lesson_url varchar(2048),
        delivery_mode enum ('ONLINE','ONSITE') not null,
        status enum ('CANCELLED','COMPLETED','IN_PROGRESS','SCHEDULED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table lifecycle_status_history (
        changed_at datetime(6) not null,
        aggregate_id varchar(26) not null,
        changed_by_user_id varchar(26),
        id varchar(26) not null,
        new_status varchar(30) not null,
        previous_status varchar(30),
        reason_detail varchar(500),
        aggregate_type enum ('COHORT','COHORT_ENROLLMENT','CONTRACT') not null,
        change_reason enum ('ADMIN_TRANSITION','AUTOMATIC_TRANSITION','CREATED','PARENT_CANCELLED','PARENT_COMPLETED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table notification_recipients (
        created_at datetime(6) not null,
        delivered_at datetime(6),
        read_at datetime(6),
        sent_at datetime(6),
        updated_at datetime(6) not null,
        id varchar(26) not null,
        notification_id varchar(26) not null,
        recipient_user_id varchar(26) not null,
        failure_reason varchar(500),
        channel enum ('EMAIL','IN_APP','PUSH') not null,
        delivery_status enum ('CANCELLED','DELIVERED','FAILED','PENDING','SENT') not null,
        primary key (id)
    ) engine=InnoDB;

    create table notifications (
        created_at datetime(6) not null,
        expires_at datetime(6),
        scheduled_at datetime(6),
        updated_at datetime(6) not null,
        audience_id varchar(26),
        context_id varchar(26),
        created_by_user_id varchar(26),
        id varchar(26) not null,
        context_type varchar(50),
        notification_type varchar(50) not null,
        title varchar(160) not null,
        action_url varchar(512),
        message text not null,
        audience_type enum ('ALL','COHORT','USER') not null,
        payload json,
        priority enum ('HIGH','LOW','NORMAL','URGENT') not null,
        primary key (id)
    ) engine=InnoDB;

    create table organization_memberships (
        end_date date,
        start_date date not null,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        id varchar(26) not null,
        organization_id varchar(26) not null,
        person_id varchar(26) not null,
        membership_role varchar(40) not null,
        job_title varchar(100),
        status enum ('ACTIVE','INACTIVE','SUSPENDED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table organization_type_assignments (
        organization_id varchar(26) not null,
        organization_type_code enum ('EMPLOYER','SCHOOL') not null,
        constraint uk_organization_type_assignments primary key (organization_id, organization_type_code)
    ) engine=InnoDB;

    create table organization_types (
        code enum ('EMPLOYER','SCHOOL') not null,
        primary key (code)
    ) engine=InnoDB;

    create table organizations (
        attendance_closing_day integer,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        tax_id varchar(14) not null,
        phone_number varchar(20) not null,
        address_id varchar(26) not null,
        id varchar(26) not null,
        parent_organization_id varchar(26),
        legal_name varchar(180) not null,
        trade_name varchar(180),
        contact_email varchar(254) not null,
        status enum ('ACTIVE','INACTIVE','SUSPENDED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table people (
        birth_date date not null,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        tax_id varchar(11) not null,
        phone_number varchar(20) not null,
        address_id varchar(26) not null,
        id varchar(26) not null,
        gender varchar(30),
        full_name varchar(150) not null,
        contact_email varchar(254),
        status enum ('ACTIVE','INACTIVE','SUSPENDED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table person_document_status_history (
        changed_at datetime(6) not null,
        changed_by_user_id varchar(26),
        id varchar(26) not null,
        person_document_id varchar(26) not null,
        change_reason enum ('ADMIN_RENEWAL','ADMIN_UPLOAD','AUTOMATIC_EXPIRATION') not null,
        new_status enum ('EXPIRED','VERIFIED') not null,
        previous_status enum ('EXPIRED','VERIFIED'),
        primary key (id)
    ) engine=InnoDB;

    create table person_documents (
        document_type_id smallint not null,
        expires_on date,
        issued_on date,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        verified_at datetime(6) not null,
        file_id varchar(26) not null,
        id varchar(26) not null,
        person_id varchar(26) not null,
        verified_by_user_id varchar(26) not null,
        document_number varchar(80),
        verification_status enum ('EXPIRED','VERIFIED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table person_type_assignments (
        person_id varchar(26) not null,
        person_type_code enum ('ADMIN','EMPLOYER_MANAGER','GUARDIAN','INSTRUCTOR','LEARNER') not null,
        constraint uk_person_type_assignments primary key (person_id, person_type_code)
    ) engine=InnoDB;

    create table person_types (
        code enum ('ADMIN','EMPLOYER_MANAGER','GUARDIAN','INSTRUCTOR','LEARNER') not null,
        primary key (code)
    ) engine=InnoDB;

    create table refresh_tokens (
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        revoked_at datetime(6),
        user_id varchar(26) not null,
        ip_address varchar(45),
        id varchar(255) not null,
        replaced_by_token varchar(255),
        token_hash varchar(255) not null,
        user_agent varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table roles (
        id smallint not null auto_increment,
        code varchar(40) not null,
        name varchar(100) not null,
        description varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table submission_files (
        created_at datetime(6) not null,
        file_id varchar(255) not null,
        submission_id varchar(255) not null,
        primary key (file_id, submission_id)
    ) engine=InnoDB;

    create table user_roles (
        role_id smallint not null,
        granted_at datetime(6) not null,
        granted_by_user_id varchar(26),
        user_id varchar(255) not null,
        primary key (role_id, user_id)
    ) engine=InnoDB;

    create table users (
        failed_authentication_attempts integer,
        regulation_accepted bit not null,
        created_at datetime(6) not null,
        email_verified_at datetime(6),
        last_login_at datetime(6),
        locked_until datetime(6),
        regulation_accepted_at datetime(6),
        token_version bigint,
        updated_at datetime(6) not null,
        id varchar(26) not null,
        person_id varchar(26) not null,
        display_name varchar(150) not null,
        login_email varchar(254) not null,
        password_hash varchar(255) not null,
        status enum ('ACTIVE','DISABLED','INVITED','LOCKED') not null,
        status_before_lock enum ('ACTIVE','DISABLED','INVITED','LOCKED'),
        primary key (id)
    ) engine=InnoDB;

    create index idx_activities_lesson_status
       on activities (lesson_id, status);

    create index idx_activities_due_status
       on activities (due_at, status);

    create index idx_activity_submissions_learner_status
       on activity_submissions (learner_id, status);

    alter table activity_submissions
       add constraint uk_activity_submissions_activity_learner unique (activity_id, learner_id);

    create index idx_attendance_records_status
       on attendance_records (status, recorded_at);

    alter table attendance_records
       add constraint uk_attendance_records_participant unique (lesson_participant_id);

    create index idx_auth_challenges_user_active
       on auth_challenges (user_id, consumed_at, expires_at);

    create index idx_auth_challenges_expiration
       on auth_challenges (expires_at);

    create index idx_cohort_enrollments_roster
       on cohort_enrollments (cohort_id, status);

    alter table cohort_enrollments
       add constraint uk_cohort_enrollments_period unique (contract_id, cohort_id, start_date);

    create index idx_cohorts_status
       on cohorts (status);

    alter table cohorts
       add constraint uk_cohorts_code unique (code);

    alter table contract_documents
       add constraint uk_contract_documents_file unique (file_id);

    alter table contract_documents
       add constraint uk_contract_documents_version unique (contract_id, document_type_id, version_number);

    create index idx_contract_school_changes_contract_time
       on contract_school_changes (contract_id, changed_at);

    create index idx_contracts_learner_period
       on contracts (learner_id, start_date);

    create index idx_contracts_employer_status
       on contracts (employer_id, status);

    create index idx_contracts_school_status
       on contracts (school_id, status);

    create index idx_contact_messages_status_created
       on contact_messages (status, created_at);

    create index idx_contact_messages_type_created
       on contact_messages (contact_type, created_at);

    alter table document_types
       add constraint uk_document_types_code unique (code);

    create index idx_files_uploaded_by_user
       on files (uploaded_by_user_id);

    create index idx_files_storage_key
       on files (storage_key);

    create index idx_files_checksum_sha256
       on files (checksum_sha256);

    alter table learners
       add constraint uk_learners_person_id unique (person_id);

    alter table learners
       add constraint uk_learners_registration_number unique (registration_number);

    create index idx_lesson_participants_learner
       on lesson_participants (learner_id, lesson_id);

    create index idx_lesson_participants_source_enrollment
       on lesson_participants (source_enrollment_id);

    alter table lesson_participants
       add constraint uk_lesson_participants_lesson_learner unique (lesson_id, learner_id);

    create index idx_lessons_instructor_start
       on lessons (instructor_person_id, starts_at);

    alter table lessons
       add constraint uk_lessons_cohort_start unique (cohort_id, starts_at);

    create index idx_lifecycle_status_history_aggregate_time
       on lifecycle_status_history (aggregate_type, aggregate_id, changed_at);

    create index idx_notification_recipients_inbox
       on notification_recipients (recipient_user_id, channel, read_at);

    create index idx_notification_recipients_delivery
       on notification_recipients (delivery_status, created_at);

    alter table notification_recipients
       add constraint uk_notification_recipients_channel unique (notification_id, recipient_user_id, channel);

    create index idx_notifications_schedule
       on notifications (scheduled_at, expires_at);

    create index idx_notifications_context
       on notifications (context_type, context_id);

    create index idx_notifications_audience
       on notifications (audience_type, audience_id);

    create index idx_organization_memberships_org_status
       on organization_memberships (organization_id, status);

    alter table organization_memberships
       add constraint uk_organization_memberships_period unique (person_id, organization_id, membership_role, start_date);

    create index idx_organization_type_assignments_type
       on organization_type_assignments (organization_type_code);

    create index idx_organizations_parent
       on organizations (parent_organization_id);

    create index idx_organizations_address
       on organizations (address_id);

    alter table organizations
       add constraint uk_organizations_tax_id unique (tax_id);

    alter table organizations
       add constraint UK8wjtwx7nw268x0ljdywku2o33 unique (address_id);

    create index idx_people_address_id
       on people (address_id);

    alter table people
       add constraint uk_people_tax_id unique (tax_id);

    alter table people
       add constraint uk_people_contact_email unique (contact_email);

    alter table people
       add constraint UK5ar305c5184xe483llce9qqf3 unique (address_id);

    create index idx_person_document_status_history_document_time
       on person_document_status_history (person_document_id, changed_at);

    create index idx_person_documents_person_type
       on person_documents (person_id, document_type_id);

    create index idx_person_documents_status_expiration
       on person_documents (verification_status, expires_on);

    alter table person_documents
       add constraint uk_person_documents_file unique (file_id);

    alter table person_documents
       add constraint uk_person_documents_person_type unique (person_id, document_type_id);

    alter table person_documents
       add constraint uk_person_documents_type_number unique (document_type_id, document_number);

    create index idx_person_type_assignments_type
       on person_type_assignments (person_type_code);

    create index idx_refresh_tokens_user_id
       on refresh_tokens (user_id);

    create index idx_refresh_tokens_token_hash
       on refresh_tokens (token_hash);

    alter table refresh_tokens
       add constraint UKo2mlirhldriil2y7krapq4frt unique (token_hash);

    alter table roles
       add constraint uk_roles_code unique (code);

    alter table users
       add constraint uk_users_login_email unique (login_email);

    alter table users
       add constraint uk_users_person_id unique (person_id);

    alter table activities
       add constraint FKliqc1qqtdj7j5ufoxg216560x
       foreign key (created_by_user_id)
       references users (id);

    alter table activities
       add constraint FK2r10ng7gpy3drh4hroow0fb4k
       foreign key (lesson_id)
       references lessons (id);

    alter table activity_files
       add constraint FKcm0vyu610nhvgoeyu0dcqrpx4
       foreign key (activity_id)
       references activities (id);

    alter table activity_files
       add constraint FKnxa0che61ww2vnyr71iv2my6x
       foreign key (file_id)
       references files (id);

    alter table activity_submissions
       add constraint FK3do6cgoad5iig7ia2ssiammr6
       foreign key (activity_id)
       references activities (id);

    alter table activity_submissions
       add constraint FKak5m1djkmpaxvg14m70p36bj6
       foreign key (graded_by_user_id)
       references users (id);

    alter table activity_submissions
       add constraint FKctro91v3ba1ty4vas163c93ee
       foreign key (learner_id)
       references learners (id);

    alter table attendance_records
       add constraint FK9bqllaufm7vcatehnuefe2cwx
       foreign key (lesson_participant_id)
       references lesson_participants (id);

    alter table attendance_records
       add constraint FKho0ct9mniflxm6wl8dg2gc9dx
       foreign key (recorded_by_user_id)
       references users (id);

    alter table auth_challenges
       add constraint FKrt7wv87etoua39qe4nvn2uxia
       foreign key (user_id)
       references users (id)
       on delete cascade;

    alter table cohort_enrollments
       add constraint FKeu8yg37omulitxc7exfqprqvf
       foreign key (cohort_id)
       references cohorts (id);

    alter table cohort_enrollments
       add constraint FKha3jdq3s2vcn62wxv77bdslng
       foreign key (contract_id)
       references contracts (id);

    alter table contract_documents
       add constraint FK209wv2ub1herf6byylbrfvcxj
       foreign key (contract_id)
       references contracts (id);

    alter table contract_documents
       add constraint FK7sm6444eq8l84hlmx7fy92jeb
       foreign key (document_type_id)
       references document_types (id);

    alter table contract_documents
       add constraint FKdu0b6fbiagmvq1ukp5aj8yg9p
       foreign key (file_id)
       references files (id);

    alter table contract_school_changes
       add constraint FKq7kir60tfabc31qdkinpw1xlo
       foreign key (contract_id)
       references contracts (id);

    alter table contracts
       add constraint FKf4h97x91lniquf5gkrowke493
       foreign key (employer_id)
       references organizations (id);

    alter table contracts
       add constraint FKok6866kqppebs3rund5bxrvkw
       foreign key (learner_id)
       references learners (id);

    alter table contracts
       add constraint FK7ec93lpp2cdhm8qldy2hhvoba
       foreign key (school_id)
       references organizations (id);

    alter table files
       add constraint FK4849ia3qe6sjfwghy2rmborly
       foreign key (uploaded_by_user_id)
       references users (id);

    alter table learner_guardians
       add constraint FKkry2vhxtk7gl2h4727gxyx0lc
       foreign key (guardian_person_id)
       references people (id);

    alter table learner_guardians
       add constraint FKewxnn3rsrde7jg0q4xunhfr34
       foreign key (learner_id)
       references learners (id);

    alter table learners
       add constraint FKltva3mk3hgp3vv2sfoo5h2r08
       foreign key (person_id)
       references people (id);

    alter table lesson_participants
       add constraint FK11hq9g1e0lgov7tjlvilr34jq
       foreign key (assigned_by_user_id)
       references users (id);

    alter table lesson_participants
       add constraint FKlxpn2n4xtaxfver5u2ycb7i5h
       foreign key (learner_id)
       references learners (id);

    alter table lesson_participants
       add constraint FKmiwuupbn7q88ehmntrvtbyx4k
       foreign key (lesson_id)
       references lessons (id);

    alter table lesson_participants
       add constraint FKlls1i4xul6ittf8twuf8kbek9
       foreign key (source_enrollment_id)
       references cohort_enrollments (id);

    alter table lessons
       add constraint FKb8wumvxyidy53ubepxtsa65w3
       foreign key (cohort_id)
       references cohorts (id);

    alter table lessons
       add constraint FKk1xa8xsp8queaj4f2du54q8v4
       foreign key (instructor_person_id)
       references people (id);

    alter table lifecycle_status_history
       add constraint FKdmnaylwvm4ftqshh57ynwrlda
       foreign key (changed_by_user_id)
       references users (id);

    alter table notification_recipients
       add constraint FKiuf5qgbttjq6ry57u1dni7qn4
       foreign key (notification_id)
       references notifications (id);

    alter table notification_recipients
       add constraint FK4ju76iqqracsb8gllihpk2ujd
       foreign key (recipient_user_id)
       references users (id);

    alter table notifications
       add constraint FKciucoi9mtan2s6xmcfrt08cln
       foreign key (created_by_user_id)
       references users (id);

    alter table organization_memberships
       add constraint FKedfr2sj5wsdjnrnwylkari8c2
       foreign key (organization_id)
       references organizations (id);

    alter table organization_memberships
       add constraint FKmhbtjdaoke71177we71b4nsfn
       foreign key (person_id)
       references people (id);

    alter table organization_type_assignments
       add constraint fk_organization_type_assignments_organization
       foreign key (organization_id)
       references organizations (id);

    alter table organization_type_assignments
       add constraint fk_organization_type_assignments_type
       foreign key (organization_type_code)
       references organization_types (code);

    alter table organizations
       add constraint FK2pcjeyffqurfmgrypdta1rnye
       foreign key (address_id)
       references addresses (id);

    alter table organizations
       add constraint FK1wrxw14jeh44fxhe2fptxd9
       foreign key (parent_organization_id)
       references organizations (id);

    alter table people
       add constraint FKbueucqaikoa3yawsdv8pnkn8p
       foreign key (address_id)
       references addresses (id);

    alter table person_document_status_history
       add constraint FKffqiar8ldl81b73vhlakyxs3
       foreign key (changed_by_user_id)
       references users (id);

    alter table person_document_status_history
       add constraint FKnb2hlqhphdh17ibkjs8ee3ffb
       foreign key (person_document_id)
       references person_documents (id);

    alter table person_documents
       add constraint FKc2x37c2hxv1pl0owg8vp4yp6l
       foreign key (document_type_id)
       references document_types (id);

    alter table person_documents
       add constraint FKnjk7975y76dislu9lbwaeqxfp
       foreign key (file_id)
       references files (id);

    alter table person_documents
       add constraint FKl90sohhixecn2v6e1d3m7721h
       foreign key (person_id)
       references people (id);

    alter table person_documents
       add constraint FKlwqxhp09tv2p69013j7rbb1it
       foreign key (verified_by_user_id)
       references users (id);

    alter table person_type_assignments
       add constraint FK8wtrxfpqmoh5ssi9bi8a4jhno
       foreign key (person_type_code)
       references person_types (code);

    alter table person_type_assignments
       add constraint FK1vx531ktujccsv6arr57ioaw8
       foreign key (person_id)
       references people (id);

    alter table refresh_tokens
       add constraint FK1lih5y2npsf8u5o3vhdb9y0os
       foreign key (user_id)
       references users (id)
       on delete cascade;

    alter table submission_files
       add constraint FKerms0byj19juv1co5d3htm17d
       foreign key (file_id)
       references files (id);

    alter table submission_files
       add constraint FKg8320levm70pbwhnmb5bq2nhb
       foreign key (submission_id)
       references activity_submissions (id);

    alter table user_roles
       add constraint FKo1mxid6f5rtbgtwjdcw9pqqd6
       foreign key (granted_by_user_id)
       references users (id);

    alter table user_roles
       add constraint FKh8ciramu9cc9q3qcqiv4ue8a6
       foreign key (role_id)
       references roles (id);

    alter table user_roles
       add constraint FKhfh9dx7w3ubf1co1vdev94g3f
       foreign key (user_id)
       references users (id);

    alter table users
       add constraint FKsv7wp99d6g5x8iisfpjf6sbpg
       foreign key (person_id)
       references people (id);

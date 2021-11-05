CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
       --;;
CREATE TABLE IF NOT EXISTS user_account (
       id serial primary key,
       email varchar(320) unique not null,
       date_created timestamp with time zone not null
       );
       --;;
CREATE TABLE IF NOT EXISTS file_type (
       id int primary key,
       name varchar(255) unique not null
       );
     --;;
INSERT INTO file_type VALUES
       (0, 'track'), (1, 'mix'), (2, 'image'), (3, 'video')
       ;
    --;;
CREATE TABLE IF NOT EXISTS access_token (
       id uuid primary key default uuid_generate_v4(),
       name varchar(255) unique,
       user_id int,
       file_type_id int,
       max_upload_count int,
       date_created timestamp with time zone not null,
       date_exp timestamp with time zone,
       note varchar
       );
       --;;
CREATE TABLE IF NOT EXISTS file_upload (
       id serial primary key,
       file_type_id int REFERENCES file_type(id),
       filepath varchar unique not null,
       filename varchar(255),
       content_type varchar(255),
       artist varchar(255),
       album varchar(255),
       display_name varchar(255),
       date_uploaded timestamp with time zone not null,
       uploaded_by int REFERENCES user_account(id),
       access_code uuid REFERENCES access_token(id)
       );
--;;
CREATE VIEW vw_access_token_usage AS
SELECT id as access_code, user_id, file_type_id, max_upload_count, date_exp,
       COALESCE(T2.upload_count, 0) as upload_count
FROM access_token T1
LEFT JOIN (SELECT access_code, count(*) as upload_count
FROM file_upload
GROUP BY access_code) T2
ON T1.id = T2.access_code;

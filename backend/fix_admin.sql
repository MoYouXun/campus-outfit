USE campus_outfit;
DELETE FROM user WHERE username = 'super_admin';
INSERT INTO user (username, password, role, nickname) 
VALUES ('super_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt.8G02', 'ADMIN', 'SuperAdmin');

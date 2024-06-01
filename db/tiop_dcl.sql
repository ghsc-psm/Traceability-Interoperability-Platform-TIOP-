CREATE ROLE tiop_rw;
GRANT SELECT, INSERT,UPDATE ON tiopdb.* TO tiop_rw;

CREATE ROLE tiop_ro;
GRANT SELECT ON tiopdb.* TO tiop_ro;

CREATE ROLE tiop_admin;
GRANT SELECT, INSERT,UPDATE, DELETE ON tiopdb.* TO tiop_admin;
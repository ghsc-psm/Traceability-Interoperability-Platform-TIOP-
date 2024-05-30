--<ScriptOptions statementTerminator=";"/>

CREATE TABLE `tiopdb`.`trading_partner` (
	`partner_id` INT NOT NULL AUTO_INCREMENT,
	`partner_parent_id` INT NOT NULL,
	`partner_name` VARCHAR(124) NOT NULL,
	`partner_type` VARCHAR(60) NOT NULL,
	`create_date` TIMESTAMP NOT NULL,
	`creator_id` VARCHAR(24) NOT NULL,
	`last_modified_date` TIMESTAMP NOT NULL,
	`last_modified_by` VARCHAR(24) NOT NULL,
	`current_indicator` CHAR(1) NOT NULL,
	`ods_text` CHAR(124),
	PRIMARY KEY (`partner_id`)
);

CREATE TABLE `tiopdb`.`location` (
	`location_id` INT NOT NULL AUTO_INCREMENT,
	`partner_id` INT NOT NULL,
	`location_type` VARCHAR(24),
	`gln` VARCHAR(13) NOT NULL,
	`address` VARCHAR(124),
	`city` VARCHAR(64),
	`state` VARCHAR(24),
	`zip_code` VARCHAR(24),
	`country_code` VARCHAR(2) NOT NULL,
	`country` VARCHAR(64) NOT NULL,
	`create_date` TIMESTAMP NOT NULL,
	`creator_id` VARCHAR(24) NOT NULL,
	`last_modified_date` TIMESTAMP NOT NULL,
	`last_modified_by` VARCHAR(24) NOT NULL,
	`current_indicator` CHAR(1) NOT NULL,
	`ods_text` VARCHAR(124),
	PRIMARY KEY (`location_id`)
);

CREATE TABLE `tiopdb`.`contact` (
	`contact_id` INT NOT NULL AUTO_INCREMENT,
	`partner_id` INT NOT NULL,
	`title` VARCHAR(12),
	`first_name` VARCHAR(64) NOT NULL,
	`middle_name` VARCHAR(64),
	`last_name` VARCHAR(64) NOT NULL,
	`phone` VARCHAR(24),
	`email` VARCHAR(64),
	`create_date` TIMESTAMP NOT NULL,
	`creator_id` VARCHAR(24) NOT NULL,
	`last_modified_date` TIMESTAMP NOT NULL,
	`last_modified_by` VARCHAR(24) NOT NULL,
	`current_indicator` CHAR(1) NOT NULL,
	`ods_text` CHAR(124),
	PRIMARY KEY (`contact_id`)
);

CREATE TABLE `tiopdb`.`trade_item` (
	`item_id` INT NOT NULL AUTO_INCREMENT,
	`item_parent_id` INT NOT NULL,
	`partner_id` INT NOT NULL,
	`gtin` VARCHAR(14) NOT NULL,
	`trade_item_unit_descriptor` VARCHAR(24) NOT NULL,
	`product_description` VARCHAR(200) NOT NULL,
	`strength` VARCHAR(64),
	`dosage_form` VARCHAR(35),
	`atc_code` VARCHAR(40),
	`net_content` FLOAT,
	`net_content_uom` VARCHAR(24),
	`child_gtin` VARCHAR(14),
	`child_gtin_quantity` INT,
	`create_date` TIMESTAMP NOT NULL,
	`creator_id` VARCHAR(24) NOT NULL,
	`last_modified_date` TIMESTAMP NOT NULL,
	`last_modified_by` VARCHAR(24) NOT NULL,
	`current_indicator` CHAR(1) NOT NULL,
	`ods_text` VARCHAR(124),
	PRIMARY KEY (`item_id`)
);

CREATE TABLE `tiopdb`.`item_registration` (
	`registration_id` INT NOT NULL AUTO_INCREMENT,
	`item_id` INT NOT NULL,
	`mah_no` VARCHAR(64) NOT NULL,
	`country_code` VARCHAR(2) NOT NULL,
	`country` VARCHAR(64) NOT NULL,
	`create_date` TIMESTAMP NOT NULL,
	`creator_id` VARCHAR(24) NOT NULL,
	`last_modified_date` TIMESTAMP NOT NULL,
	`last_modified_by` VARCHAR(24) NOT NULL,
	`current_indicator` CHAR(1) NOT NULL,
	`ods_text` VARCHAR(124),
	PRIMARY KEY (`registration_id`)
);

CREATE TABLE `tiopdb`.`tiop_rule` (
	`rule_id` INT NOT NULL AUTO_INCREMENT,
	`item_id` INT NOT NULL,
	`registration_id` INT NOT NULL,
	`source_location_id` INT NOT NULL,
	`location_id` INT NOT NULL,
	`status_id` INT NOT NULL,
	`moh_no` VARCHAR(64) NOT NULL,
	`gtin` VARCHAR(14) NOT NULL,
	`source_location_gln` VARCHAR(13) NOT NULL,
	`destination_location_gln` VARCHAR(13) NOT NULL,
	`create_date` TIMESTAMP NOT NULL,
	`creator_id` VARCHAR(24) NOT NULL,
	`last_modified_date` TIMESTAMP NOT NULL,
	`last_modified_by` VARCHAR(24) NOT NULL,
	`current_indicator` CHAR(1) NOT NULL,
	`ods_text` VARCHAR(124),
	PRIMARY KEY (`rule_id`)
);

CREATE TABLE `tiopdb`.`tiop_operation` (
	`operation_id` BIGINT NOT NULL AUTO_INCREMENT,
	`event_type_id` INT NOT NULL,
	`event_id` VARCHAR(64),
	`source_partner_id` INT NOT NULL,
	`destination_partner_id` INT NOT NULL,
	`source_location_id` INT NOT NULL,
	`destination_location_id` INT NOT NULL,
	`item_id` INT NOT NULL,
	`rule_id` INT NOT NULL,
	`status_id` INT NOT NULL,
	`create_date` TIMESTAMP NOT NULL,
	`creator_id` VARCHAR(24) NOT NULL,
	`last_modified_date` TIMESTAMP NOT NULL,
	`last_modified_by` VARCHAR(24) NOT NULL,
	`current_indicator` CHAR(1) NOT NULL,
	`ods_text` VARCHAR(124),
	PRIMARY KEY (`operation_id`)
);

CREATE TABLE `tiopdb`.`tiop_status` (
	`status_id` INT NOT NULL AUTO_INCREMENT,
	`status_description` VARCHAR(64) NOT NULL,
	`create_date` TIMESTAMP NOT NULL,
	`creator_id` VARCHAR(24) NOT NULL,
	`last_modified_date` TIMESTAMP NOT NULL,
	`last_modified_by` VARCHAR(24) NOT NULL,
	`current_indicator` CHAR(1) NOT NULL,
	`ods_text` VARCHAR(124),
	PRIMARY KEY (`status_id`)
);

CREATE TABLE `tiopdb`.`event_type` (
	`event_type_id` INT NOT NULL AUTO_INCREMENT,
	`event_type_description` VARCHAR(64) NOT NULL,
	`create_date` TIMESTAMP NOT NULL,
	`creator_id` VARCHAR(24) NOT NULL,
	`last_modified_date` TIMESTAMP NOT NULL,
	`last_modified_by` VARCHAR(24) NOT NULL,
	`current_indicator` CHAR(1) NOT NULL,
	`ods_text` VARCHAR(124),
	PRIMARY KEY (`event_type_id`)
);

ALTER TABLE `tiopdb`.`trading_partner` ADD CONSTRAINT `trading_partner_FK` FOREIGN KEY (`partner_parent_id`)
	REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
	ON DELETE CASCADE;

ALTER TABLE `tiopdb`.`location` ADD CONSTRAINT `location_trading_partner_FK` FOREIGN KEY (`partner_id`)
	REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`contact` ADD CONSTRAINT `contact_trading_partner_FK` FOREIGN KEY (`partner_id`)
	REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`trade_item` ADD CONSTRAINT `trade_item_trading_partner_FK` FOREIGN KEY (`partner_id`)
	REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`trade_item` ADD CONSTRAINT `trade_item_FK` FOREIGN KEY (`item_parent_id`)
	REFERENCES `tiopdb`.`trade_item` (`item_id`)
	ON DELETE CASCADE;

ALTER TABLE `tiopdb`.`item_registration` ADD CONSTRAINT `item_registration_trade_item_FK` FOREIGN KEY (`item_id`)
	REFERENCES `tiopdb`.`trade_item` (`item_id`)
	ON DELETE CASCADE;

ALTER TABLE `tiopdb`.`tiop_rule` ADD CONSTRAINT `tiop_rule_trade_item_FK` FOREIGN KEY (`item_id`)
	REFERENCES `tiopdb`.`trade_item` (`item_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_rule` ADD CONSTRAINT `tiop_rule_item_registration_FK` FOREIGN KEY (`registration_id`)
	REFERENCES `tiopdb`.`item_registration` (`registration_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_rule` ADD CONSTRAINT `tiop_rule_location_FK` FOREIGN KEY (`source_location_id`)
	REFERENCES `tiopdb`.`location` (`location_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_rule` ADD CONSTRAINT `tiop_rule_location_FK1` FOREIGN KEY (`location_id`)
	REFERENCES `tiopdb`.`location` (`location_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_rule` ADD CONSTRAINT `tiop_rule_tiop_status_FK` FOREIGN KEY (`status_id`)
	REFERENCES `tiopdb`.`tiop_status` (`status_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_operation` ADD CONSTRAINT `tiop_operation_trading_partner_FK` FOREIGN KEY (`source_partner_id`)
	REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_operation` ADD CONSTRAINT `tiop_operation_trading_partner_FK1` FOREIGN KEY (`destination_partner_id`)
	REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_operation` ADD CONSTRAINT `tiop_operation_location_FK` FOREIGN KEY (`source_location_id`)
	REFERENCES `tiopdb`.`location` (`location_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_operation` ADD CONSTRAINT `tiop_operation_location_FK1` FOREIGN KEY (`destination_location_id`)
	REFERENCES `tiopdb`.`location` (`location_id`)
	ON DELETE CASCADE;

ALTER TABLE `tiopdb`.`tiop_operation` ADD CONSTRAINT `tiop_operation_trade_item_FK` FOREIGN KEY (`item_id`)
	REFERENCES `tiopdb`.`trade_item` (`item_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_operation` ADD CONSTRAINT `tiop_operation_tiop_rule_association_FK` FOREIGN KEY (`rule_id`)
	REFERENCES `tiopdb`.`tiop_rule` (`rule_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_operation` ADD CONSTRAINT `tiop_operation_event_type_FK` FOREIGN KEY (`event_type_id`)
	REFERENCES `tiopdb`.`event_type` (`event_type_id`)
	ON DELETE RESTRICT;

ALTER TABLE `tiopdb`.`tiop_operation` ADD CONSTRAINT `tiop_operation_tiop_status_FK` FOREIGN KEY (`status_id`)
	REFERENCES `tiopdb`.`tiop_status` (`status_id`)
	ON DELETE RESTRICT;


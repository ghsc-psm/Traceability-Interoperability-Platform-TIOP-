-- -----------------------------------------------------
-- Schema tiopdb
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS `tiopdb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci ;
USE `tiopdb` ;

-- -----------------------------------------------------
-- Table `tiopdb`.`trading_partner`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`trading_partner` (
  `partner_id` INT NOT NULL AUTO_INCREMENT,
  `partner_parent_id` INT NULL DEFAULT NULL,
  `partner_name` VARCHAR(124) NOT NULL,
  `partner_type` VARCHAR(60) NOT NULL,
  `create_date` TIMESTAMP NOT NULL,
  `creator_id` VARCHAR(24) NOT NULL,
  `last_modified_date` TIMESTAMP NOT NULL,
  `last_modified_by` VARCHAR(24) NOT NULL,
  `current_indicator` CHAR(1) NOT NULL,
  `ods_text` CHAR(124) NULL DEFAULT NULL,
  PRIMARY KEY (`partner_id`),
  INDEX `trading_partner_FK` (`partner_parent_id` ASC) VISIBLE,
  CONSTRAINT `trading_partner_FK`
    FOREIGN KEY (`partner_parent_id`)
    REFERENCES `tiopdb`.`trading_partner` (`partner_id`))
ENGINE = InnoDB
AUTO_INCREMENT = 100
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `tiopdb`.`contact`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`contact` (
  `contact_id` INT NOT NULL AUTO_INCREMENT,
  `partner_id` INT NOT NULL,
  `title` VARCHAR(12) NULL DEFAULT NULL,
  `first_name` VARCHAR(64) NOT NULL,
  `middle_name` VARCHAR(64) NULL DEFAULT NULL,
  `last_name` VARCHAR(64) NOT NULL,
  `phone` VARCHAR(24) NULL DEFAULT NULL,
  `email` VARCHAR(64) NULL DEFAULT NULL,
  `create_date` TIMESTAMP NOT NULL,
  `creator_id` VARCHAR(24) NOT NULL,
  `last_modified_date` TIMESTAMP NOT NULL,
  `last_modified_by` VARCHAR(24) NOT NULL,
  `current_indicator` CHAR(1) NOT NULL,
  `ods_text` CHAR(124) NULL DEFAULT NULL,
  PRIMARY KEY (`contact_id`),
  INDEX `contact_trading_partner_FK` (`partner_id` ASC) VISIBLE,
  CONSTRAINT `contact_trading_partner_FK`
    FOREIGN KEY (`partner_id`)
    REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
    ON DELETE RESTRICT)
ENGINE = InnoDB
AUTO_INCREMENT = 100
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `tiopdb`.`event_type`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`event_type` (
  `event_type_id` INT NOT NULL AUTO_INCREMENT,
  `event_type_description` VARCHAR(64) NOT NULL,
  `create_date` TIMESTAMP NOT NULL,
  `creator_id` VARCHAR(24) NOT NULL,
  `last_modified_date` TIMESTAMP NOT NULL,
  `last_modified_by` VARCHAR(24) NOT NULL,
  `current_indicator` CHAR(1) NOT NULL,
  `ods_text` VARCHAR(124) NULL DEFAULT NULL,
  PRIMARY KEY (`event_type_id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `tiopdb`.`trade_item`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`trade_item` (
  `item_id` INT NOT NULL AUTO_INCREMENT,
  `item_child_id` INT NULL DEFAULT NULL,
  `partner_id` INT NOT NULL,
  `gtin` VARCHAR(14) NOT NULL,
  `trade_item_unit_descriptor` VARCHAR(24) NOT NULL,
  `product_description` VARCHAR(200) NOT NULL,
  `strength` VARCHAR(64) NULL DEFAULT NULL,
  `dosage_form` VARCHAR(35) NULL DEFAULT NULL,
  `atc_code` VARCHAR(40) NULL DEFAULT NULL,
  `net_content` FLOAT NULL DEFAULT NULL,
  `net_content_uom` VARCHAR(24) NULL DEFAULT NULL,
  `child_gtin` VARCHAR(14) NULL DEFAULT NULL,
  `child_gtin_quantity` INT NULL DEFAULT NULL,
  `create_date` TIMESTAMP NOT NULL,
  `creator_id` VARCHAR(24) NOT NULL,
  `last_modified_date` TIMESTAMP NOT NULL,
  `last_modified_by` VARCHAR(24) NOT NULL,
  `current_indicator` CHAR(1) NOT NULL,
  `ods_text` VARCHAR(124) NULL DEFAULT NULL,
  `gtin_uri` VARCHAR(52) NULL DEFAULT NULL,
  PRIMARY KEY (`item_id`),
  INDEX `trade_item_trading_partner_FK` (`partner_id` ASC) VISIBLE,
  INDEX `trade_item_FK` (`item_child_id` ASC) VISIBLE,
  CONSTRAINT `trade_item_FK`
    FOREIGN KEY (`item_child_id`)
    REFERENCES `tiopdb`.`trade_item` (`item_id`)
    ON DELETE CASCADE,
  CONSTRAINT `trade_item_trading_partner_FK`
    FOREIGN KEY (`partner_id`)
    REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
    ON DELETE RESTRICT)
ENGINE = InnoDB
AUTO_INCREMENT = 100
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `tiopdb`.`item_registration`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`item_registration` (
  `registration_id` INT NOT NULL AUTO_INCREMENT,
  `item_id` INT NOT NULL,
  `ma_agency` VARCHAR(80) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NOT NULL,
  `ma_item_identification` VARCHAR(80) NULL DEFAULT NULL,
  `ma_type_code` VARCHAR(80) NULL DEFAULT NULL,
  `ma_permit_no` VARCHAR(200) NULL DEFAULT NULL,
  `ma_permit_start_date` DATE NULL DEFAULT NULL,
  `ma_permit_end_date` DATE NULL DEFAULT NULL,
  `ma_status_description` VARCHAR(200) NULL DEFAULT NULL,
  `ma_status_language_code` VARCHAR(80) NULL DEFAULT NULL,
  `country_code` VARCHAR(2) NOT NULL,
  `country` VARCHAR(64) NOT NULL,
  `create_date` TIMESTAMP NOT NULL,
  `creator_id` VARCHAR(24) NOT NULL,
  `last_modified_date` TIMESTAMP NOT NULL,
  `last_modified_by` VARCHAR(24) NOT NULL,
  `current_indicator` CHAR(1) NOT NULL,
  `ods_text` VARCHAR(124) NULL DEFAULT NULL,
  PRIMARY KEY (`registration_id`),
  INDEX `item_registration_trade_item_FK` (`item_id` ASC) VISIBLE,
  CONSTRAINT `item_registration_trade_item_FK`
    FOREIGN KEY (`item_id`)
    REFERENCES `tiopdb`.`trade_item` (`item_id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 100
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `tiopdb`.`location`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`location` (
  `location_id` INT NOT NULL AUTO_INCREMENT,
  `partner_id` INT NOT NULL,
  `location_type` VARCHAR(24) NULL DEFAULT NULL,
  `gln` VARCHAR(13) NOT NULL,
  `address` VARCHAR(124) NULL DEFAULT NULL,
  `city` VARCHAR(64) NULL DEFAULT NULL,
  `state` VARCHAR(24) NULL DEFAULT NULL,
  `zip_code` VARCHAR(24) NULL DEFAULT NULL,
  `country_code` VARCHAR(2) NOT NULL,
  `country` VARCHAR(64) NOT NULL,
  `create_date` TIMESTAMP NOT NULL,
  `creator_id` VARCHAR(24) NOT NULL,
  `last_modified_date` TIMESTAMP NOT NULL,
  `last_modified_by` VARCHAR(24) NOT NULL,
  `current_indicator` CHAR(1) NOT NULL,
  `ods_text` VARCHAR(124) NULL DEFAULT NULL,
  `gln_uri` VARCHAR(50) NULL DEFAULT NULL,
  PRIMARY KEY (`location_id`),
  INDEX `location_trading_partner_FK` (`partner_id` ASC) VISIBLE,
  CONSTRAINT `location_trading_partner_FK`
    FOREIGN KEY (`partner_id`)
    REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
    ON DELETE RESTRICT)
ENGINE = InnoDB
AUTO_INCREMENT = 100
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `tiopdb`.`tiop_status`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`tiop_status` (
  `status_id` INT NOT NULL AUTO_INCREMENT,
  `status_description` VARCHAR(64) NOT NULL,
  `create_date` TIMESTAMP NOT NULL,
  `creator_id` VARCHAR(24) NOT NULL,
  `last_modified_date` TIMESTAMP NOT NULL,
  `last_modified_by` VARCHAR(24) NOT NULL,
  `current_indicator` CHAR(1) NOT NULL,
  `ods_text` VARCHAR(124) NULL DEFAULT NULL,
  PRIMARY KEY (`status_id`))
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `tiopdb`.`tiop_rule`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`tiop_rule` (
  `rule_id` INT NOT NULL AUTO_INCREMENT,
  `item_id` INT NOT NULL,
  `registration_id` INT NOT NULL,
  `source_location_id` INT NOT NULL,
  `destination_location_id` INT NOT NULL,
  `status_id` INT NOT NULL,
  `ma_permit_no` VARCHAR(200) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NOT NULL,
  `gtin` VARCHAR(14) NOT NULL,
  `source_gln` VARCHAR(13) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NOT NULL,
  `destination_gln` VARCHAR(13) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NOT NULL,
  `create_date` TIMESTAMP NOT NULL,
  `creator_id` VARCHAR(24) NOT NULL,
  `last_modified_date` TIMESTAMP NOT NULL,
  `last_modified_by` VARCHAR(24) NOT NULL,
  `current_indicator` CHAR(1) NOT NULL,
  `ods_text` VARCHAR(124) NULL DEFAULT NULL,
  `source_gln_uri` VARCHAR(50) NOT NULL,
  `destination_gln_rui` VARCHAR(50) NOT NULL,
  `gtin_uri` VARCHAR(52) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NOT NULL,
  PRIMARY KEY (`rule_id`),
  INDEX `tiop_rule_trade_item_FK` (`item_id` ASC) VISIBLE,
  INDEX `tiop_rule_location_FK` (`source_location_id` ASC) VISIBLE,
  INDEX `tiop_rule_location_FK1` (`destination_location_id` ASC) VISIBLE,
  INDEX `tiop_rule_tiop_status_FK` (`status_id` ASC) VISIBLE,
  INDEX `tiop_rule_item_registration_FK` (`registration_id` ASC) VISIBLE,
  CONSTRAINT `tiop_rule_item_registration_FK`
    FOREIGN KEY (`registration_id`)
    REFERENCES `tiopdb`.`item_registration` (`registration_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_rule_location_FK`
    FOREIGN KEY (`source_location_id`)
    REFERENCES `tiopdb`.`location` (`location_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_rule_location_FK1`
    FOREIGN KEY (`destination_location_id`)
    REFERENCES `tiopdb`.`location` (`location_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_rule_tiop_status_FK`
    FOREIGN KEY (`status_id`)
    REFERENCES `tiopdb`.`tiop_status` (`status_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_rule_trade_item_FK`
    FOREIGN KEY (`item_id`)
    REFERENCES `tiopdb`.`trade_item` (`item_id`)
    ON DELETE RESTRICT)
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `tiopdb`.`tiop_operation`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`tiop_operation` (
  `operation_id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_type_id` INT NULL DEFAULT NULL,
  `event_id` VARCHAR(64) NULL DEFAULT NULL,
  `source_partner_id` INT NULL DEFAULT NULL,
  `destination_partner_id` INT NULL DEFAULT NULL,
  `source_location_id` INT NULL DEFAULT NULL,
  `destination_location_id` INT NULL DEFAULT NULL,
  `item_id` INT NULL DEFAULT NULL,
  `rule_id` INT NULL DEFAULT NULL,
  `status_id` INT NOT NULL,
  `create_date` TIMESTAMP NOT NULL,
  `creator_id` VARCHAR(24) NOT NULL,
  `last_modified_date` TIMESTAMP NOT NULL,
  `last_modified_by` VARCHAR(24) NOT NULL,
  `current_indicator` CHAR(1) NOT NULL,
  `ods_text` VARCHAR(124) NULL DEFAULT NULL,
  `exception_detail` VARCHAR(254) NULL DEFAULT NULL,
  `object_event_count` INT NULL DEFAULT NULL,
  `aggregation_event_count` INT NULL DEFAULT NULL,
  PRIMARY KEY (`operation_id`),
  INDEX `tiop_operation_trading_partner_FK` (`source_partner_id` ASC) VISIBLE,
  INDEX `tiop_operation_trading_partner_FK1` (`destination_partner_id` ASC) VISIBLE,
  INDEX `tiop_operation_location_FK` (`source_location_id` ASC) VISIBLE,
  INDEX `tiop_operation_location_FK1` (`destination_location_id` ASC) VISIBLE,
  INDEX `tiop_operation_trade_item_FK` (`item_id` ASC) VISIBLE,
  INDEX `tiop_operation_tiop_rule_association_FK` (`rule_id` ASC) VISIBLE,
  INDEX `tiop_operation_event_type_FK` (`event_type_id` ASC) VISIBLE,
  INDEX `tiop_operation_tiop_status_FK` (`status_id` ASC) VISIBLE,
  CONSTRAINT `tiop_operation_event_type_FK`
    FOREIGN KEY (`event_type_id`)
    REFERENCES `tiopdb`.`event_type` (`event_type_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_operation_location_FK`
    FOREIGN KEY (`source_location_id`)
    REFERENCES `tiopdb`.`location` (`location_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_operation_location_FK1`
    FOREIGN KEY (`destination_location_id`)
    REFERENCES `tiopdb`.`location` (`location_id`)
    ON DELETE CASCADE,
  CONSTRAINT `tiop_operation_tiop_rule_association_FK`
    FOREIGN KEY (`rule_id`)
    REFERENCES `tiopdb`.`tiop_rule` (`rule_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_operation_tiop_status_FK`
    FOREIGN KEY (`status_id`)
    REFERENCES `tiopdb`.`tiop_status` (`status_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_operation_trade_item_FK`
    FOREIGN KEY (`item_id`)
    REFERENCES `tiopdb`.`trade_item` (`item_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_operation_trading_partner_FK`
    FOREIGN KEY (`source_partner_id`)
    REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
    ON DELETE RESTRICT,
  CONSTRAINT `tiop_operation_trading_partner_FK1`
    FOREIGN KEY (`destination_partner_id`)
    REFERENCES `tiopdb`.`trading_partner` (`partner_id`)
    ON DELETE RESTRICT)
ENGINE = InnoDB
AUTO_INCREMENT = 1
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;

USE `tiopdb` ;

-- -----------------------------------------------------
-- Placeholder table for view `tiopdb`.`item_master_vw`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`item_master_vw` (`partner_name` INT, `partner_type` INT, `gtin` INT, `trade_item_unit_descriptor` INT, `product_description` INT, `strength` INT, `dosage_form` INT, `atc_code` INT, `net_content` INT, `net_content_uom` INT, `child_gtin` INT, `child_gtin_quantity` INT, `ma_agency` INT, `ma_item_identification` INT, `ma_type_code` INT, `ma_permit_no` INT, `ma_permit_start_date` INT, `ma_permit_end_date` INT, `ma_status_description` INT, `ma_status_language_code` INT, `country` INT);

-- -----------------------------------------------------
-- Placeholder table for view `tiopdb`.`tiop_rule_vw`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`tiop_rule_vw` (`rule_id` INT, `tp_source_name` INT, `tp_source_type` INT, `tp_source_gln` INT, `tp_source_country` INT, `tp_destination_name` INT, `tp_destination_type` INT, `tp_destination_gln` INT, `tp_destination_country` INT, `ti_gtin` INT, `trade_item_unit_descriptor` INT, `product_description` INT, `ma_agency` INT, `ir_ma_permit_no` INT, `country` INT, `status_description` INT, `source_gln` INT, `destination_gln` INT, `gtin` INT, `ma_permit_no` INT, `source_gln_uri` INT, `destination_gln_rui` INT, `gtin_uri` INT);

-- -----------------------------------------------------
-- Placeholder table for view `tiopdb`.`trading_partner_vw`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiopdb`.`trading_partner_vw` (`partner_id` INT, `partner_parent_id` INT, `partner_name` INT, `partner_type` INT, `title` INT, `first_name` INT, `last_name` INT, `phone` INT, `email` INT, `location_type` INT, `gln` INT, `address` INT, `city` INT, `state` INT, `zip_code` INT, `country_code` INT, `country` INT);

-- -----------------------------------------------------
-- View `tiopdb`.`item_master_vw`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tiopdb`.`item_master_vw`;
USE `tiopdb`;
CREATE  OR REPLACE ALGORITHM=UNDEFINED DEFINER=`admin`@`%` SQL SECURITY DEFINER VIEW `tiopdb`.`item_master_vw` AS select `tp`.`partner_name` AS `partner_name`,`tp`.`partner_type` AS `partner_type`,`ti`.`gtin` AS `gtin`,`ti`.`trade_item_unit_descriptor` AS `trade_item_unit_descriptor`,`ti`.`product_description` AS `product_description`,`ti`.`strength` AS `strength`,`ti`.`dosage_form` AS `dosage_form`,`ti`.`atc_code` AS `atc_code`,`ti`.`net_content` AS `net_content`,`ti`.`net_content_uom` AS `net_content_uom`,`ti`.`child_gtin` AS `child_gtin`,`ti`.`child_gtin_quantity` AS `child_gtin_quantity`,`ir`.`ma_agency` AS `ma_agency`,`ir`.`ma_item_identification` AS `ma_item_identification`,`ir`.`ma_type_code` AS `ma_type_code`,`ir`.`ma_permit_no` AS `ma_permit_no`,`ir`.`ma_permit_start_date` AS `ma_permit_start_date`,`ir`.`ma_permit_end_date` AS `ma_permit_end_date`,`ir`.`ma_status_description` AS `ma_status_description`,`ir`.`ma_status_language_code` AS `ma_status_language_code`,`ir`.`country` AS `country` from ((`tiopdb`.`trade_item` `ti` join `tiopdb`.`item_registration` `ir` on((`ti`.`item_id` = `ir`.`item_id`))) join `tiopdb`.`trading_partner` `tp` on((`ti`.`partner_id` = `tp`.`partner_id`))) where (`ti`.`current_indicator` = 'A');

-- -----------------------------------------------------
-- View `tiopdb`.`tiop_rule_vw`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tiopdb`.`tiop_rule_vw`;
USE `tiopdb`;
CREATE  OR REPLACE ALGORITHM=UNDEFINED DEFINER=`admin`@`%` SQL SECURITY DEFINER VIEW `tiopdb`.`tiop_rule_vw` AS select `tr`.`rule_id` AS `rule_id`,`stp`.`partner_name` AS `tp_source_name`,`stp`.`partner_type` AS `tp_source_type`,`sl`.`gln` AS `tp_source_gln`,`sl`.`country` AS `tp_source_country`,`dtp`.`partner_name` AS `tp_destination_name`,`dtp`.`partner_type` AS `tp_destination_type`,`dl`.`gln` AS `tp_destination_gln`,`dl`.`country` AS `tp_destination_country`,`ti`.`gtin` AS `ti_gtin`,`ti`.`trade_item_unit_descriptor` AS `trade_item_unit_descriptor`,`ti`.`product_description` AS `product_description`,`ir`.`ma_agency` AS `ma_agency`,`ir`.`ma_permit_no` AS `ir_ma_permit_no`,`ir`.`country` AS `country`,`ts`.`status_description` AS `status_description`,`tr`.`source_gln` AS `source_gln`,`tr`.`destination_gln` AS `destination_gln`,`tr`.`gtin` AS `gtin`,`tr`.`ma_permit_no` AS `ma_permit_no`,`tr`.`source_gln_uri` AS `source_gln_uri`,`tr`.`destination_gln_rui` AS `destination_gln_rui`,`tr`.`gtin_uri` AS `gtin_uri` from (((((((`tiopdb`.`tiop_rule` `tr` join `tiopdb`.`trade_item` `ti` on((`tr`.`item_id` = `ti`.`item_id`))) join `tiopdb`.`item_registration` `ir` on((`tr`.`registration_id` = `ir`.`registration_id`))) join `tiopdb`.`location` `sl` on((`tr`.`source_location_id` = `sl`.`location_id`))) join `tiopdb`.`location` `dl` on((`tr`.`destination_location_id` = `dl`.`location_id`))) join `tiopdb`.`trading_partner` `stp` on((`sl`.`partner_id` = `stp`.`partner_id`))) join `tiopdb`.`trading_partner` `dtp` on((`dl`.`partner_id` = `dtp`.`partner_id`))) join `tiopdb`.`tiop_status` `ts` on((`tr`.`status_id` = `ts`.`status_id`)));

-- -----------------------------------------------------
-- View `tiopdb`.`trading_partner_vw`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tiopdb`.`trading_partner_vw`;
USE `tiopdb`;
CREATE  OR REPLACE ALGORITHM=UNDEFINED DEFINER=`admin`@`%` SQL SECURITY DEFINER VIEW `tiopdb`.`trading_partner_vw` AS select `tp`.`partner_id` AS `partner_id`,`tp`.`partner_parent_id` AS `partner_parent_id`,`tp`.`partner_name` AS `partner_name`,`tp`.`partner_type` AS `partner_type`,`c`.`title` AS `title`,`c`.`first_name` AS `first_name`,`c`.`last_name` AS `last_name`,`c`.`phone` AS `phone`,`c`.`email` AS `email`,`l`.`location_type` AS `location_type`,`l`.`gln` AS `gln`,`l`.`address` AS `address`,`l`.`city` AS `city`,`l`.`state` AS `state`,`l`.`zip_code` AS `zip_code`,`l`.`country_code` AS `country_code`,`l`.`country` AS `country` from ((`tiopdb`.`trading_partner` `tp` join `tiopdb`.`contact` `c` on((`tp`.`partner_id` = `c`.`partner_id`))) join `tiopdb`.`location` `l` on((`tp`.`partner_id` = `l`.`partner_id`))) where ((`tp`.`current_indicator` = 'A') and (`c`.`current_indicator` = 'A') and (`l`.`current_indicator` = 'A'));

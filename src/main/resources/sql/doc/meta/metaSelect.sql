SELECT * FROM `${PREFIX}_document_meta` where `entityId`=? AND `domainId` = ? AND `key` = ? FOR UPDATE;

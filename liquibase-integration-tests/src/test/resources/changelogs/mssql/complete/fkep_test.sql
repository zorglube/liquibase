create schema MYTESTSCHEMA;

create table MYTESTSCHEMA.MY_OTHER_TABLE (MY_FK_COLUMN int NOT NULL);

alter table MYTESTSCHEMA.MY_OTHER_TABLE
    add constraint MY_OTHER_TABLE_PK primary key (MY_FK_COLUMN);

create table MYTESTSCHEMA.MY_FKP_TEST_TABLE (MY_FK_COLUMN int);

alter table MYTESTSCHEMA.MY_FKP_TEST_TABLE
    add constraint MY_FOREIGN_KEY foreign key (MY_FK_COLUMN)
        references MYTESTSCHEMA.MY_OTHER_TABLE (MY_FK_COLUMN);

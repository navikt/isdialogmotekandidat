CREATE TABLE DIALOGMOTESTATUS
(
    id          SERIAL PRIMARY KEY,
    uuid        CHAR(36)    NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL,
    personident VARCHAR(11) NOT NULL,
    mote_tidspunkt  timestamptz NOT NULL,
    ferdigstilt_tidspunkt  timestamptz NOT NULL
);

CREATE INDEX IX_DIALOGMOTESTATUS_PERSONIDENT on DIALOGMOTESTATUS (personident);

GRANT SELECT ON DIALOGMOTESTATUS TO cloudsqliamuser;
GRANT SELECT ON DIALOGMOTESTATUS TO "isyfo-analyse";

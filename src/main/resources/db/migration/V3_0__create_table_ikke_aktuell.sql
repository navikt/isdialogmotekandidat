CREATE TABLE IKKE_AKTUELL
(
    id          SERIAL PRIMARY KEY,
    uuid        CHAR(36)    NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL,
    created_by  VARCHAR(7)  NOT NULL,
    personident VARCHAR(11) NOT NULL,
    arsak       VARCHAR(40) NOT NULL,
    beskrivelse TEXT
);

CREATE INDEX IX_IKKE_AKTUELL_PERSONIDENT on IKKE_AKTUELL (personident);

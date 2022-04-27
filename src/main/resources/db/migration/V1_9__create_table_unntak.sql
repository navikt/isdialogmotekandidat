CREATE TABLE UNNTAK
(
    id          SERIAL PRIMARY KEY,
    uuid        CHAR(36)    NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL,
    created_by  VARCHAR(7)  NOT NULL,
    personident VARCHAR(11) NOT NULL,
    arsak       VARCHAR(40) NOT NULL
);

CREATE INDEX IX_UNNTAK_PERSONIDENT on UNNTAK (personident);

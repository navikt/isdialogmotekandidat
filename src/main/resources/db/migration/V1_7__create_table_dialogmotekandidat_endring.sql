CREATE TABLE DIALOGMOTEKANDIDAT_ENDRING
(
    id          SERIAL PRIMARY KEY,
    uuid        CHAR(36)    NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL,
    personident VARCHAR(11) NOT NULL,
    kandidat    BOOLEAN     NOT NULL,
    arsak       VARCHAR(20) NOT NULL
);

CREATE INDEX IX_DIALOGMOTEKANDIDAT_ENDRING_PERSONIDENT on DIALOGMOTEKANDIDAT_ENDRING (personident);

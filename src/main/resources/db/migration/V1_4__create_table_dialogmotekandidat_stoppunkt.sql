CREATE TABLE DIALOGMOTEKANDIDAT_STOPPPUNKT
(
    id                 SERIAL PRIMARY KEY,
    uuid               CHAR(36)    NOT NULL UNIQUE,
    created_at         timestamptz NOT NULL,
    personident        VARCHAR(11) NOT NULL,
    processed_at       timestamptz,
    status             VARCHAR(20) NOT NULL,
    stoppunkt_planlagt DATE        NOT NULL
);

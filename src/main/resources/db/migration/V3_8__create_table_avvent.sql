CREATE TABLE AVVENT
(
    id          SERIAL PRIMARY KEY,
    uuid        CHAR(36)    NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL,
    frist       DATE        NOT NULL,
    created_by  VARCHAR(7)  NOT NULL,
    personident VARCHAR(11) NOT NULL,
    beskrivelse TEXT
);

CREATE INDEX IX_AVVENT_PERSONIDENT on AVVENT(personident);

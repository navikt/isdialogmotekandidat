CREATE TABLE OPPFOLGINGSTILFELLE_ARBEIDSTAKER
(
    id                                SERIAL      PRIMARY KEY,
    uuid                              CHAR(36)    NOT NULL UNIQUE,
    created_at                        timestamptz NOT NULL,
    personident                       VARCHAR(11) NOT NULL,
    tilfelle_generated_at             timestamptz NOT NULL,
    tilfelle_start                    DATE        NOT NULL,
    tilfelle_end                      DATE        NOT NULL,
    referanse_tilfelle_bit_uuid       CHAR(36)    NOT NULL,
    referanse_tilfelle_bit_inntruffet timestamptz NOT NULL
);

CREATE INDEX IX_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_PERSONIDENT on OPPFOLGINGSTILFELLE_ARBEIDSTAKER (personident);

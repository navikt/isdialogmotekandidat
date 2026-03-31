-- Close all but the last created avvent per personident
UPDATE avvent
SET is_lukket = TRUE
WHERE is_lukket = FALSE
AND id NOT IN (
    SELECT DISTINCT ON (personident) id
    FROM avvent
    ORDER BY personident, created_at DESC
);

-- Close avvent rows created before an unntak for the same person
UPDATE avvent
SET is_lukket = TRUE
WHERE is_lukket = FALSE
  AND EXISTS (
    SELECT 1 FROM unntak
    WHERE unntak.personident = avvent.personident
      AND unntak.created_at > avvent.created_at
);

-- Close avvent rows created before an ikke_aktuell for the same person
UPDATE avvent
SET is_lukket = TRUE
WHERE is_lukket = FALSE
  AND EXISTS (
    SELECT 1 FROM ikke_aktuell
    WHERE ikke_aktuell.personident = avvent.personident
      AND ikke_aktuell.created_at > avvent.created_at
);

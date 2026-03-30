UPDATE avvent
SET is_lukket = TRUE
WHERE id NOT IN (
    SELECT DISTINCT ON (personident) id
    FROM avvent
    ORDER BY personident, created_at DESC
);

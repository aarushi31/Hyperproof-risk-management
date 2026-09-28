-- Optional date by which the risk must next be reviewed. Kept as a separate migration (V1 is already applied).
ALTER TABLE risks ADD COLUMN next_review_date DATE;

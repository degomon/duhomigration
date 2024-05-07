CREATE OR REPLACE FUNCTION icc_extract_birthdate(column_value text)
  RETURNS date
  AS $$
DECLARE
  cleaned_value text;
  birthdate text;
BEGIN
  -- Remove spaces and non-alphanumeric characters from the input
  cleaned_value := regexp_replace(column_value, '[^a-zA-Z0-9]', '', 'g');
  -- If the cleaned value is null or not 14 characters long, return null
  IF cleaned_value IS NULL OR length(cleaned_value) <> 14 THEN
    RETURN NULL;
  END IF;
  -- Extract the substring representing the birthdate
  birthdate := substring(cleaned_value FROM 4 FOR 6);
  -- If the birthdate substring is empty or null, return null
  IF birthdate IS NULL OR birthdate = '' THEN
    RETURN NULL;
  END IF;
  -- Convert the birthdate substring to a date in ddMMYY format
  BEGIN
    RETURN to_date(birthdate, 'DDMMYY');
  EXCEPTION
    WHEN OTHERS THEN
      -- If the birthdate substring is not parseable to date, return null
      RETURN NULL;
  END;
END;

$$
LANGUAGE plpgsql;


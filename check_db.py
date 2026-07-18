import mysql.connector

try:
    conn = mysql.connector.connect(
        host="localhost",
        user="root",
        password="123456",
        database="attendance_db"
    )
    cursor = conn.cursor()
    cursor.execute("SELECT cccd, full_name, LENGTH(image_raw_url), LEFT(image_raw_url, 20) FROM employees WHERE full_name LIKE '%Ngọc Anh%'")
    row = cursor.fetchone()
    if row:
        print(f"CCCD: {row[0]}")
        print(f"Name: {row[1]}")
        print(f"Image Len: {row[2]}")
        print(f"Start: {row[3]}")
    else:
        print("Not found")
    conn.close()
except Exception as e:
    print(f"Error: {e}")

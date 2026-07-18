import mysql.connector
try:
    conn = mysql.connector.connect(host='localhost', user='root', password='123456', database='attendance_db')
    cursor = conn.cursor()
    cursor.execute("SELECT cccd, full_name FROM employees WHERE cccd = '304002150'")
    print(f"Result for 304002150: {cursor.fetchone()}")
    cursor.execute("SELECT cccd, full_name FROM employees WHERE cccd = '025304002150'")
    print(f"Result for 025304002150: {cursor.fetchone()}")
    conn.close()
except Exception as e:
    print(f"Error: {e}")

import mysql.connector
try:
    conn = mysql.connector.connect(host='localhost', user='root', password='123456', database='attendance_db')
    cursor = conn.cursor()
    cursor.execute("SELECT cccd, employee_name, scan_time FROM attendance_logs WHERE employee_name LIKE '%Ngọc Anh%' ORDER BY scan_time DESC LIMIT 5")
    print(f"Recent logs: {cursor.fetchall()}")
    conn.close()
except Exception as e:
    print(f"Error: {e}")

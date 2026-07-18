import mysql.connector

conn = mysql.connector.connect(
    host='192.168.1.190',
    user='attendance_user',
    password='Attendance2024',
    database='attendance_db'
)
cur = conn.cursor()

# Delete test OUT log - CCCD: 025304002150, direction OUT, from 2026-06-22
cur.execute("DELETE FROM attendance_logs WHERE cccd='025304002150' AND direction='OUT' AND DATE(scan_time)='2026-06-22' LIMIT 1")
conn.commit()

print(f'✅ Deleted {cur.rowcount} test log record(s)')

# Show remaining logs for this CCCD on 2026-06-22
cur.execute("SELECT id, cccd, direction, status, alert_type, scan_time FROM attendance_logs WHERE cccd='025304002150' AND DATE(scan_time)='2026-06-22' ORDER BY scan_time DESC LIMIT 5")
print("\n📋 Remaining logs for CCCD 025304002150:")
print("ID | Direction | Status | AlertType | Time")
print("-" * 60)
for row in cur.fetchall():
    print(f"{row[0]} | {row[2]} | {row[3]} | {row[4]} | {row[5]}")

cur.close()
conn.close()

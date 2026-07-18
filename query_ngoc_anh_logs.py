import mysql.connector
import datetime

def query_logs():
    try:
        conn = mysql.connector.connect(
            host='localhost',
            user='root',
            password='123456',
            database='attendance_db'
        )
        cursor = conn.cursor(dictionary=True)
        
        # Query for employee 1 (Ngọc Anh)
        cccd = '025304002150'
        query = """
        SELECT id, cccd, captured_name, scan_time, status, notes 
        FROM attendance_logs 
        WHERE cccd = %s 
        ORDER BY scan_time DESC 
        LIMIT 20
        """
        cursor.execute(query, (cccd,))
        rows = cursor.fetchall()
        
        print(f"Recent logs for {cccd}:")
        for row in rows:
            print(row)
            
        conn.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    query_logs()

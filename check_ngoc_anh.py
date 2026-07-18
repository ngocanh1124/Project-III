import mysql.connector

try:
    conn = mysql.connector.connect(
        host="localhost",
        user="root",
        password="123456",
        database="attendance_db"
    )
    cursor = conn.cursor(dictionary=True)
    
    print("--- Searching for employee 'Ngọc Anh' ---")
    cursor.execute("SELECT * FROM employees WHERE full_name LIKE '%Ngọc Anh%'")
    employees = cursor.fetchall()
    
    if not employees:
        print("No employee found with name matching 'Ngọc Anh'. Trying 'Ngoc Anh'...")
        cursor.execute("SELECT * FROM employees WHERE full_name LIKE '%Ngoc Anh%'")
        employees = cursor.fetchall()

    if employees:
        for emp in employees:
            emp_id = emp['id']
            print(f"Employee Found: ID={emp_id}, Name={emp['full_name']}, CCCD={emp['cccd']}")
            # Try to get activation status if column exists
            try:
                print(f"Status (from col 'is_active'): {emp.get('is_active', 'N/A')}")
                print(f"Status (from col 'activation_status'): {emp.get('activation_status', 'N/A')}")
            except:
                pass
            
            print(f"\n--- Checking access_permissions for ID={emp_id} ---")
            cursor.execute("SELECT * FROM access_permissions WHERE employee_id = %s", (emp_id,))
            perms = cursor.fetchall()
            if perms:
                for p in perms:
                    print(f"Permission: DeviceID={p['device_id']}, Start={p['start_date']}, End={p['end_date']}, Active={p['is_active']}")
            else:
                print("No entries in access_permissions for this employee.")
            print("-" * 30)
    else:
        print("Employee not found.")
        
    conn.close()
except Exception as e:
    print(f"Error: {e}")

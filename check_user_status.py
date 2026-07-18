import mysql.connector

try:
    conn = mysql.connector.connect(
        host='localhost',
        user='root',
        password='123456',
        database='attendance_db'
    )
    cursor = conn.cursor(dictionary=True)
    
    # Check for the user
    query = "SELECT id, full_name, cccd, activation_status, image_raw_url FROM employees WHERE full_name LIKE '%Ngọc Anh%'"
    cursor.execute(query)
    results = cursor.fetchall()
    
    if not results:
        print("User not found.")
    for row in results:
        print(f"ID: {row['id']}")
        print(f"Name: {row['full_name']}")
        print(f"CCCD: {row['cccd']}")
        print(f"Status: {row['activation_status']}")
        has_image = "Yes (starting with " + row['image_raw_url'][:30] + ")" if row['image_raw_url'] else "No"
        print(f"Has Image: {has_image}")
        print("-" * 20)
        
    conn.close()
except Exception as e:
    print(f"Error: {e}")

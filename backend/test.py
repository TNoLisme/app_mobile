import pyodbc

server = "localhost,1433"   # hoặc "localhost\\SQLEXPRESS"
database = "Mobile"
username = "sa"
password = "123456"

conn_str = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    f"SERVER={server};"
    f"DATABASE={database};"
    f"UID={username};"
    f"PWD={password};"
)

try:
    conn = pyodbc.connect(conn_str, timeout=5)
    print("✅ Kết nối thành công!")

    cursor = conn.cursor()
    cursor.execute("SELECT @@VERSION;")

    row = cursor.fetchone()
    print("SQL Server version:")
    print(row[0])

    conn.close()

except Exception as e:
    print("❌ Kết nối thất bại:")
    print(e)
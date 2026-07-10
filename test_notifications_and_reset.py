import os

cred_path = "/root/MobileJobCard/firebase-service-account.json"

print("Credential path:", cred_path)
print("Exists:", os.path.exists(cred_path))
print("Is file:", os.path.isfile(cred_path))
print("Readable:", os.access(cred_path, os.R_OK))

if os.path.exists(cred_path):
    print("File size:", os.path.getsize(cred_path), "bytes")
else:
    print("❌ File not found!")
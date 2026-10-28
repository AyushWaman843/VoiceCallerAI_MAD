package com.ayush.madv2.ui

import android.content.Context
import android.provider.ContactsContract

internal fun loadPhoneContacts(context: Context): List<ContactDraft> {
    val contactsByIdentity = linkedMapOf<Pair<String, String>, ContactDraft>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.Contacts.IN_VISIBLE_GROUP,
    )

    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        "${ContactsContract.Contacts.IN_VISIBLE_GROUP}=1",
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        var nextId = 1L

        while (cursor.moveToNext()) {
            val rawName = cursor.getString(nameIndex).orEmpty().trim()
            val rawNumber = cursor.getString(numberIndex).orEmpty().trim()
            val normalizedNumber = normalizePhoneNumber(rawNumber)
            val identity = rawName.lowercase() to normalizedNumber
            if (rawName.isBlank() || normalizedNumber.isBlank() || contactsByIdentity.containsKey(identity)) {
                continue
            }
            contactsByIdentity[identity] = ContactDraft(
                id = nextId++,
                name = rawName,
                number = normalizedNumber,
            )
        }
    }

    return contactsByIdentity.values.toList()
}

private fun normalizePhoneNumber(rawNumber: String): String {
    val digits = rawNumber.filter { it.isDigit() }
    return when {
        digits.length == 10 && digits.first() in "6789" -> "+91$digits"
        digits.length == 12 && digits.startsWith("91") -> "+$digits"
        digits.length == 11 && digits.startsWith("0") && digits[1] in "6789" -> "+91${digits.drop(1)}"
        rawNumber.trim().startsWith("+") && digits.isNotBlank() -> rawNumber.trim()
        else -> rawNumber.trim()
    }
}

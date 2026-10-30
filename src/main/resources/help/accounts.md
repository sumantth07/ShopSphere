# Accounts and login

## Registering with email and password
Create an account from the Register page with your full name, email and a password (8 to 72 characters with at least one letter and one number). We email a 6-digit verification code. Enter it on the verification page to activate the account. You cannot sign in until the email is verified.

## Verification codes
Codes expire after 10 minutes, allow 5 wrong attempts, and a new code can be requested once per minute. After too many wrong attempts, request a new code.

## Signing in with Google
Choose Continue with Google on the login page. If no ShopSphere account uses that email, one is created and verified automatically. If an account with the same email already exists, ShopSphere does not merge them automatically: we email a 6-digit code to that address and you must enter it to link Google to the existing account. After linking, both Google and email/password sign-in open the same account.

## Setting or resetting a password
Use Forgot password. We email a code; entering it lets you choose a new password. This is also how an account created with Google can add a password for email sign-in. Resetting a password signs out all other sessions.

## Sessions
Access tokens last 15 minutes and are renewed automatically with a secure HttpOnly cookie. Logging out revokes that session.

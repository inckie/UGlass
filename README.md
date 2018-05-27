
# Uber client for Google Glass

This is Glassware for Uber.
Right now it supports login, profile displaying, and carrying out full ongoing ride lifecycle, i.e. ride request is not implemented.
You'll need to register as an Uber developer to run the application.
I haven't checked it outside the sandbox yet, but I suppose that developer account will actually work.
Please read <a href="https://developer.uber.com/docs/riders/guides/scopes">docs on Privileged scopes</a>.

## Building
Project consist of 3 modules:
* **glass** - Google Glass application
* **mobile** - companion application for mobile device where login is performed
* **shared** - a shared module with couple lines of code

### Build steps
* Clone the repo and open it in Android Studio. Most likely you'll need to install Glass Development Kit add-on.
* Register as an <a href="https://developer.uber.com/">Uber developer</a>
* Register Android application and obtain UBER_CLIENT_ID and UBER_REDIRECT_URI (that one can be http://127.0.0.1 for testing). Don't forget about signing fingerprints!
* Enable all the scopes but history related and request_receipt
* Put UBER_CLIENT_ID and UBER_REDIRECT_URI to the 'uber.properties' in the root of the project.
* Build and deploy both executables

## Running
For a number of reasons, Uber Android library is not suitable for Glass (and does not link, anyway), so there is a companion app for performing Uber auth procedure.<br/>
For the same reason generic Java API SDK is used on the Glass.<br/>
So, the access token is obtained on the phone and then passed to the Glass wia Bluetooth connection.

* Launch the companion application on the phone paired with your Glass, and login to Uber
* If you've got a bottom sheet popup with login error on Uber authorization screen, do not tap close. Just tap outside the window, scroll the text up, and press 'Accept'
* Launch Glass application and tap to start listening for the token
* Press 'Send' on the phone
* Glass will (hopefully) receive the token and login into your account

Now you can start a (test) ride in Uber dev console. Tap on profile card to check for ongoing trips. If there are any, you'll be redirected to Ride LiveCard.<br/>

[![This is how it looks like in sandbox](https://img.youtube.com/vi/cPU1qpEXEe0/0.jpg)](https://www.youtube.com/watch?v=cPU1qpEXEe0)

## Future plans
* Test the app on real ride outside the sandbox
* Implement ride request

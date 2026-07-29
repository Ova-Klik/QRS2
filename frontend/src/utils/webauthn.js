// WebAuthn Biometric helpers using the Web Authentication API
// Uses navigator.credentials.create() for registration
// and navigator.credentials.get() for authentication

const RP_NAME = 'Tech School'
const RP_ID = window.location.hostname // 'localhost' in dev

function bufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return window.btoa(binary)
}

function base64ToBuffer(base64) {
  const binary = window.atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

function generateChallenge() {
  const challenge = new Uint8Array(32)
  window.crypto.getRandomValues(challenge)
  return challenge
}

// Check if WebAuthn is supported
export function isWebAuthnSupported() {
  return window.PublicKeyCredential !== undefined &&
    typeof window.PublicKeyCredential === 'function'
}

// Check if platform authenticator (fingerprint/face) is available
export async function isPlatformAuthenticatorAvailable() {
  if (!isWebAuthnSupported()) return false
  try {
    return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()
  } catch {
    return false
  }
}

// Register a new biometric credential
export async function registerBiometric(userId) {
  if (!isWebAuthnSupported()) {
    throw new Error('WebAuthn is not supported in this browser')
  }

  const challenge = generateChallenge()
  const userIdBuffer = new TextEncoder().encode(userId)

  const createOptions = {
    publicKey: {
      challenge,
      rp: { name: RP_NAME, id: RP_ID },
      user: {
        id: userIdBuffer,
        name: userId,
        displayName: userId,
      },
      pubKeyCredParams: [
        { alg: -7, type: 'public-key' },   // ES256
        { alg: -257, type: 'public-key' },  // RS256
      ],
      authenticatorSelection: {
        authenticatorAttachment: 'platform',
        userVerification: 'required',
        residentKey: 'preferred',
      },
      timeout: 60000,
      attestation: 'none',
    },
  }

  const credential = await navigator.credentials.create(createOptions)

  return {
    credentialId: bufferToBase64(credential.rawId),
    publicKey: bufferToBase64(credential.response.getPublicKey()),
    clientDataJSON: bufferToBase64(credential.response.clientDataJSON),
    attestationObject: bufferToBase64(credential.response.attestationObject),
  }
}

// Authenticate with biometric (verify)
export async function authenticateBiometric(credentialId) {
  if (!isWebAuthnSupported()) {
    throw new Error('WebAuthn is not supported in this browser')
  }

  const challenge = generateChallenge()

  const getOptions = {
    publicKey: {
      challenge,
      rpId: RP_ID,
      allowCredentials: credentialId ? [{
        id: base64ToBuffer(credentialId),
        type: 'public-key',
        transports: ['internal'],
      }] : [],
      userVerification: 'required',
      timeout: 60000,
    },
  }

  const assertion = await navigator.credentials.get(getOptions)

  return {
    credentialId: bufferToBase64(assertion.rawId),
    authenticatorData: bufferToBase64(assertion.response.authenticatorData),
    clientDataJSON: bufferToBase64(assertion.response.clientDataJSON),
    signature: bufferToBase64(assertion.response.signature),
  }
}

// Get network info (WiFi SSID via Network Information API)
export function getNetworkInfo() {
  const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection
  const info = {
    ssid: null,
    type: connection?.type || null,
    effectiveType: connection?.effectiveType || null,
  }

  // WiFi SSID is only available in some contexts (e.g., Chrome on Android with permissions)
  // In most cases, we can't access the SSID directly from the browser for security reasons
  // We'll rely on IP-based network validation as the primary method
  if (connection?.ssid) {
    info.ssid = connection.ssid
  }

  return info
}

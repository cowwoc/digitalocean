package com.github.cowwoc.digitalocean.internal.util;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceParser;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.DSAPublicKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.jcajce.spec.OpenSSHPublicKeySpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObjectGenerator;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.Base64;
import java.util.Collection;
import java.util.StringJoiner;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;

/**
 * SSH helper functions.
 */
@SuppressWarnings({"checkstyle:javadocblocktaglocation", "checkstyle:javadocmissingleadingasterisk",
	"checkstyle:javadocmissingwhitespaceafterasterisk", "checkstyle:javadoctagcontinuationindentation",
	"checkstyle:nonemptyatclausedescription", "checkstyle:requireemptylinebeforeblocktaggroup",
	"checkstyle:singlelinejavadoc"})
// WORKAROUND: https://github.com/checkstyle/checkstyle/issues/16005
public class SshKeys
{
	/**
	 * The name of the ECDSA (Elliptic Curve Digital Signature Algorithm).
	 *
	 * @see <a
	 * 	href="https://docs.oracle.com/en/java/javase/23/docs/specs/security/standard-names.html#keypairgenerator-algorithms">
	 * 	KeyPairGenerator Algorithms</a>.
	 */
	private static final String ELLIPTIC_CURVE_ALGORITHM = "EC";

	/**
	 * Creates a new instance.
	 */
	public SshKeys()
	{
	}

	/**
	 * Returns a temporary randomly generated KeyPair.
	 *
	 * @return the KeyPair
	 */
	public KeyPair createTempKeyPair()
	{
		try
		{
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ELLIPTIC_CURVE_ALGORITHM,
				PROVIDER_NAME);
			// Curve size chosen taking https://www.keylength.com/en/compare/ into consideration
			// https://docs.oracle.com/en/java/javase/23/docs/specs/security/standard-names.html#parameterspec-names
			keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
			return keyPairGenerator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e)
		{
			// This is a deployment-time decision. Either the JVM supports the algorithm, provider, parameter, or
			// it doesn't.
			throw new AssertionError(e);
		}
	}

	/**
	 * Reads a {@code KeyPair} from a PEM-encoded PKCS#8 format stream.
	 *
	 * @param in the stream to read from
	 * @return the {@code KeyPair}
	 * @throws NullPointerException     if {@code in} is null
	 * @throws IOException              if an error occurs while reading the stream
	 * @throws GeneralSecurityException if the parsed data cannot be converted to a key pair
	 */
	public KeyPair readKeyPairAsPkcs8(InputStream in) throws IOException, GeneralSecurityException
	{
		try (InputStreamReader reader = new InputStreamReader(in);
		     PEMParser parser = new PEMParser(reader))
		{
			Object object = parser.readObject();
			if (object == null)
				throw new EOFException("No more entries found");
			if (object instanceof PrivateKeyInfo privateKeyInfo)
			{
				JcaPEMKeyConverter pemToJca = new JcaPEMKeyConverter();
				PrivateKey privateKey = pemToJca.getPrivateKey(privateKeyInfo);
				PublicKey publicKey = getPublicKeyFromPrivateKey(privateKey);
				return new KeyPair(publicKey, privateKey);
			}
			if (object instanceof PEMKeyPair pemKeyPair)
			{
				JcaPEMKeyConverter pemToJca = new JcaPEMKeyConverter();
				return pemToJca.getKeyPair(pemKeyPair);
			}
			throw new GeneralSecurityException("The stream must contain a KeyPair.\n" +
				"Actual: " + object.getClass().getName());
		}
	}

	/**
	 * @param privateKey a private key
	 * @return the public key corresponding to the private key
	 * @throws GeneralSecurityException if the key pair is unsupported or invalid
	 */
	private static PublicKey getPublicKeyFromPrivateKey(PrivateKey privateKey) throws GeneralSecurityException
	{
		if (privateKey instanceof ECPrivateKey ecPrivateKey)
		{
			KeyFactory keyFactory = KeyFactory.getInstance(privateKey.getAlgorithm());
			ECPrivateKeySpec ecPrivateKeySpec = keyFactory.getKeySpec(privateKey, ECPrivateKeySpec.class);
			ECParameterSpec ecParameterSpec = ecPrivateKeySpec.getParams();

			java.security.spec.ECPoint ecPoint = getEcPoint(ecPrivateKey, ecParameterSpec);
			ECPublicKeySpec ecPublicKeySpec = new ECPublicKeySpec(ecPoint, ecParameterSpec);
			return keyFactory.generatePublic(ecPublicKeySpec);
		}
		KeyFactory keyFactory = KeyFactory.getInstance(privateKey.getAlgorithm());
		X509EncodedKeySpec x509KeySpec = keyFactory.getKeySpec(privateKey, X509EncodedKeySpec.class);
		return keyFactory.generatePublic(x509KeySpec);
	}

	/**
	 * Writes a {@code KeyPair} as a PEM-encoded PKCS#8 format stream.
	 *
	 * @param keyPair the key pair
	 * @param writer  the stream to write into
	 * @throws NullPointerException if any of the arguments are null
	 * @throws IOException          if an error occurs while reading the stream
	 */
	public void writeKeyPairAsPkcs8(KeyPair keyPair, Writer writer) throws IOException
	{
		byte[] encodedKey = keyPair.getPrivate().getEncoded();
		PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(encodedKey);
		PemObjectGenerator pkcs8Object = new PKCS8Generator(privateKeyInfo, null);
		try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer))
		{
			pemWriter.writeObject(pkcs8Object);
		}
	}

	/**
	 * Reads a {@code PublicKey} from a PEM-encoded DER format stream.
	 *
	 * @param in the stream to read from
	 * @return the {@code PublicKey}
	 * @throws NullPointerException if {@code in} is null
	 * @throws IOException          if an error occurs while reading the stream
	 */
	public PublicKey readPublicKeyAsDer(InputStream in) throws IOException
	{
		try (InputStreamReader reader = new InputStreamReader(in);
		     PEMParser parser = new PEMParser(reader))
		{
			Object object = parser.readObject();
			if (object == null)
				throw new EOFException("No more entries found");
			SubjectPublicKeyInfo publicKeyInfo;
			if (object instanceof PEMKeyPair keyPair)
				publicKeyInfo = keyPair.getPublicKeyInfo();
			else
				publicKeyInfo = SubjectPublicKeyInfo.getInstance(object);
			JcaPEMKeyConverter pemToJca = new JcaPEMKeyConverter();
			return pemToJca.getPublicKey(publicKeyInfo);
		}
	}

	/**
	 * Writes a {@code PublicKey} as a PEM-encoded DER format stream.
	 *
	 * @param publicKey the key
	 * @param writer    the stream to write into
	 * @throws NullPointerException if any of the arguments are null
	 * @throws IOException          if an error occurs while writing into the stream
	 */
	public void writePublicKeyAsDer(PublicKey publicKey, Writer writer) throws IOException
	{
		PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(publicKey.getEncoded());
		SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(pkcs8KeySpec.getEncoded());
		try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer))
		{
			pemWriter.writeObject(publicKeyInfo);
		}
	}

	/**
	 * Reads one or more {@code KeyPair}s from a PEM-encoded OpenSSH format stream.
	 *
	 * @param in the stream to read from
	 * @return the {@code KeyPair}s in the stream
	 * @throws NullPointerException     if {@code reader} is null
	 * @throws IOException              if an error occurs while reading the stream
	 * @throws GeneralSecurityException if the parsed data cannot be converted to a key pair
	 */
	public Collection<KeyPair> readKeyPairAsOpenSsh(InputStream in) throws IOException, GeneralSecurityException
	{
		KeyPairResourceParser parser = SecurityUtils.getKeyPairResourceParser();
		return parser.loadKeyPairs(null, () -> "in", null, in);
	}

	/**
	 * Writes a {@code KeyPair} as a PEM-encoded OpenSSH format stream.
	 *
	 * @param keys a key pair
	 * @param out  the stream to write into
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IOException              if an error occurs while reading the stream
	 * @throws GeneralSecurityException if the key pair is unsupported or invalid
	 */
	public void writeKeyPairAsOpenSsh(Collection<KeyPair> keys, OutputStream out)
		throws IOException, GeneralSecurityException
	{
		OpenSSHKeyPairResourceWriter openSshWriter = OpenSSHKeyPairResourceWriter.INSTANCE;
		for (KeyPair key : keys)
			openSshWriter.writePrivateKey(key, null, null, out);
	}

	/**
	 * Writes a {@code PublicKey} as a PEM-encoded OpenSSH format stream.
	 *
	 * @param publicKey the public key
	 * @param out       the stream to write into
	 * @param comment   the comment to append to the end of the key
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IOException              if an error occurs while writing into the stream
	 * @throws GeneralSecurityException if the key pair is unsupported or invalid
	 */
	public void writePublicKeyAsOpenSsh(PublicKey publicKey, String comment, OutputStream out)
		throws IOException, GeneralSecurityException
	{
		requireThat(comment, "comment").isStripped();
		try (OutputStreamWriter writer = new OutputStreamWriter(out))
		{
			writer.write(PublicKeyEntry.toString(publicKey));
			if (!comment.isEmpty())
				writer.write(" " + comment);
		}
		catch (IllegalArgumentException e)
		{
			throw new GeneralSecurityException(e);
		}
	}

	/**
	 * Writes a {@code PublicKey} fingerprint as a OpenSSH format stream.
	 *
	 * @param publicKey     a public key
	 * @param messageDigest the digest algorithm to use (e.g. MD5 or SHA256)
	 * @param out           the stream to write into
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IOException              if an error occurs while writing into the stream
	 * @throws GeneralSecurityException if the key pair is unsupported or invalid
	 */
	public void writeFingerprint(PublicKey publicKey, MessageDigest messageDigest, OutputStream out)
		throws IOException, GeneralSecurityException
	{
		String keyAsBase64 = getKeyAsBase64(publicKey);
		byte[] keyBytes = Base64.getDecoder().decode(keyAsBase64);
		byte[] fingerprintAsBytes = messageDigest.digest(keyBytes);

		try (Writer writer = new OutputStreamWriter(out))
		{
			writer.write(getKeySize(publicKey) + " ");

			String algorithm = messageDigest.getAlgorithm();
			String fingerprint;
			if (algorithm.equals("MD5"))
			{
				StringJoiner hexFingerprint = new StringJoiner(":");
				hexFingerprint.add(algorithm);
				for (byte b : fingerprintAsBytes)
					hexFingerprint.add(String.format("%02x", b));
				fingerprint = hexFingerprint.toString();
			}
			else
			{
				fingerprint = algorithm + ":" + Base64.getEncoder().withoutPadding().
					encodeToString(fingerprintAsBytes);
			}
			writer.write(fingerprint);
		}
	}

	/**
	 * Returns the base64-encoded OpenSSH representation of the key.
	 *
	 * @param publicKey the key
	 * @return the base64-encoded OpenSSH representation of the key.
	 * @throws GeneralSecurityException if the key pair is unsupported or invalid
	 */
	private String getKeyAsBase64(PublicKey publicKey) throws GeneralSecurityException
	{
		String publicKeyAsOpenSsh;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream())
		{
			writePublicKeyAsOpenSsh(publicKey, "", out);
			publicKeyAsOpenSsh = out.toString();
		}
		catch (IOException e)
		{
			// Exception never thrown by ByteArrayOutputStream
			throw new AssertionError(e);
		}
		// Extract the base64 part of the OpenSSH public key
		return publicKeyAsOpenSsh.split(" ")[1];
	}

	/**
	 * Returns the key size in bits.
	 *
	 * @param key a key
	 * @return the key size in bits
	 */
	private int getKeySize(PublicKey key)
	{
		return switch (key)
		{
			case RSAPublicKey rsa -> rsa.getModulus().bitLength();
			case ECPublicKey ec -> ec.getParams().getCurve().getField().getFieldSize();
			case DSAPublicKey dsa -> dsa.getParams().getP().bitLength();
			default -> throw new UnsupportedOperationException("Unsupported type: " + key.getClass().getName());
		};
	}

	/**
	 * Loads a {@code PublicKey} from a stream using OpenSSH format.
	 *
	 * @param in the stream to read from
	 * @return the {@code PublicKey}
	 * @throws NullPointerException     if {@code in} is null
	 * @throws IOException              if an error occurs while reading the stream
	 * @throws GeneralSecurityException if the parsed data cannot be converted to a key pair
	 */
	public PublicKey readPublicKeyAsOpenSsh(InputStream in) throws IOException, GeneralSecurityException
	{
		String keyAsString = new String(in.readAllBytes(), UTF_8);

		// Based on https://stackoverflow.com/a/78782985/14731
		String encodedKey = keyAsString.split("\\s+")[1];
		AsymmetricKeyParameter keyParameter = OpenSSHPublicKeyUtil.parsePublicKey(
			Base64.getDecoder().decode(encodedKey));
		String algorithm = getAlgorithm(keyParameter);
		OpenSSHPublicKeySpec publicKeySpec = new OpenSSHPublicKeySpec(
			OpenSSHPublicKeyUtil.encodePublicKey(keyParameter));
		return KeyFactory.getInstance(algorithm).generatePublic(publicKeySpec);
	}

	/**
	 * Returns the name of a key's algorithm.
	 *
	 * @param keyParameter a key's parameters
	 * @return the name of the key's algorithm
	 * @throws IOException if the algorithm is unsupported
	 */
	private static String getAlgorithm(AsymmetricKeyParameter keyParameter) throws IOException
	{
		return switch (keyParameter)
		{
			case RSAKeyParameters _ -> "RSA";
			case DSAPublicKeyParameters _ -> "DSA";
			case ECPublicKeyParameters _ -> "EC";
			default -> throw new AssertionError("Unsupported key parameter: " +
				keyParameter.getClass().getName());
		};
	}

	/**
	 * Converts a private key to a public key, if possible.
	 *
	 * @param privateKey the private key
	 * @return the public key
	 * @throws GeneralSecurityException if the key pair is unsupported or invalid
	 */
	@SuppressWarnings("PMD.LocalVariableNamingConventions")
	public PublicKey convertToPublicKey(PrivateKey privateKey) throws GeneralSecurityException
	{
		// Get the key factory based on the private key algorithm
		KeyFactory keyFactory = KeyFactory.getInstance(privateKey.getAlgorithm(), PROVIDER_NAME);
		return switch (privateKey)
		{
			case ECPrivateKey ecPrivateKey ->
			{
				ECParameterSpec ecSpec = ecPrivateKey.getParams();

				// Extract the curve parameters
				java.security.spec.ECPoint q = getEcPoint(ecPrivateKey, ecSpec);

				// Create the public key spec
				ECPublicKeySpec pubSpec = new ECPublicKeySpec(q, ecSpec);
				yield keyFactory.generatePublic(pubSpec);
			}
			default -> throw new GeneralSecurityException("Unsupported type: " + privateKey.getClass().getName());
		};
	}

	/**
	 * Calculate the public key point of a private key.
	 *
	 * @param privateKey the private key
	 * @param ecSpec     the key's domain parameters
	 * @return the key's public point
	 */
	private static java.security.spec.ECPoint getEcPoint(ECPrivateKey privateKey, ECParameterSpec ecSpec)
	{
		BigInteger p = ((ECFieldFp) ecSpec.getCurve().getField()).getP();
		BigInteger a = ecSpec.getCurve().getA();
		BigInteger b = ecSpec.getCurve().getB();
		BigInteger order = ecSpec.getOrder();
		BigInteger cofactor = BigInteger.valueOf(ecSpec.getCofactor());
		ECCurve curve = new ECCurve.Fp(p, a, b, order, cofactor);

		// Get the generator point
		java.security.spec.ECPoint generator = ecSpec.getGenerator();
		ECPoint g = curve.createPoint(generator.getAffineX(), generator.getAffineY());

		// Calculate the public key point
		BigInteger s = privateKey.getS();
		ECPoint q = g.multiply(s).normalize();
		return new java.security.spec.ECPoint(q.getAffineXCoord().toBigInteger(),
			q.getAffineYCoord().toBigInteger());
	}
}
/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.sftp;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mule.extension.file.common.api.FileWriteMode.APPEND;
import static org.mule.extension.file.common.api.FileWriteMode.OVERWRITE;
import static org.mule.extension.sftp.SftpServer.PASSWORD;
import static org.mule.extension.sftp.SftpServer.USERNAME;
import static org.mule.extension.sftp.internal.SftpUtils.normalizePath;
import static org.mule.extension.sftp.internal.SftpUtils.resolvePath;
import static org.mule.extension.sftp.random.alg.PRNGAlgorithm.SHA1PRNG;

import org.mule.extension.AbstractSftpTestHarness;
import org.mule.extension.file.common.api.FileAttributes;
import org.mule.extension.sftp.api.SftpFileAttributes;
import org.mule.extension.sftp.internal.connection.SftpClient;
import org.mule.extension.sftp.internal.connection.SftpClientFactory;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.test.extension.file.common.api.FileTestHarness;

import com.jcraft.jsch.JSchException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

/**
 * Implementation of {@link FileTestHarness} for classic SFTP connections
 *
 * @since 1.0
 */
public class SftpTestHarness extends AbstractSftpTestHarness {

  private static final String SFTP_PORT = "SFTP_PORT";

  private TemporaryFolder temporaryFolder = new TemporaryFolder();
  private DynamicPort sftpPort = new DynamicPort(SFTP_PORT);
  private SftpServer sftpServer;
  private SftpClient sftpClient;
  private AuthConfigurator<SftpClient> clientAuthConfigurator;
  private AuthConfigurator<SftpServer> serverAuthConfigurator;

  /**
   * Creates a new instance which activates the {@code sftp} spring profile
   */
  public SftpTestHarness() {
    this(AuthType.USER_PASSWORD);
  }

  public SftpTestHarness(AuthType authType) {
    super("sftp");
    switch (authType) {
      case USER_PASSWORD:
        clientAuthConfigurator = (sftpClient -> sftpClient.setPassword(PASSWORD));
        serverAuthConfigurator = (SftpServer::setPasswordAuthenticator);
        break;
      case PUBLIC_KEY:
        clientAuthConfigurator = (sftpClient -> sftpClient.setIdentity(resolvePath("sftp-test-key"), null));
        serverAuthConfigurator = (sftpServer -> sftpServer
            .setPublicKeyAuthenticator(new AuthorizedKeysAuthenticator(new File(resolvePath("sftp-test-key.pub")))));
    }
  }

  /**
   * Starts a SFTP server and connects a client to it
   */
  @Override
  protected void doBefore() throws Exception {
    temporaryFolder.create();
    System.setProperty(WORKING_DIR_SYSTEM_PROPERTY, temporaryFolder.getRoot().getAbsolutePath());
    setUpServer();
    sftpClient = createDefaultSftpClient();
  }

  /**
   * Disconnects the client and shuts the server down
   */
  @Override
  protected void doAfter() throws Exception {
    try {
      if (sftpClient != null) {
        sftpClient.disconnect();
      }

      if (sftpServer != null) {
        sftpServer.stop();
      }
    } finally {
      temporaryFolder.delete();
      System.clearProperty(WORKING_DIR_SYSTEM_PROPERTY);
    }
  }

  private SftpClient createDefaultSftpClient() throws IOException, JSchException {
    SftpClient sftpClient = new SftpClientFactory().createInstance("localhost", sftpPort.getNumber(), SHA1PRNG);
    clientAuthConfigurator.configure(sftpClient);
    sftpClient.login(USERNAME);
    sftpClient.changeWorkingDirectory(temporaryFolder.getRoot().getPath());
    return sftpClient;
  }

  private void setUpServer() {
    sftpServer = new SftpServer(sftpPort.getNumber());
    serverAuthConfigurator.configure(sftpServer);
    sftpServer.start();
  }

  /**
   * @return {@link #sftpPort}
   */
  @Override
  protected TestRule[] getChildRules() {
    return new TestRule[] {sftpPort};
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void createHelloWorldFile() throws Exception {
    final String dir = "files";
    makeDir(dir);
    write(dir, HELLO_FILE_NAME, HELLO_WORLD);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void createBinaryFile() throws Exception {
    sftpClient.write(BINARY_FILE_NAME, new ByteArrayInputStream(HELLO_WORLD.getBytes()), OVERWRITE);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void makeDir(String directoryPath) throws Exception {
    sftpClient.mkdir(directoryPath);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getWorkingDirectory() throws Exception {
    return normalizePath(sftpClient.getWorkingDirectory());
  }

  public String getRootDirectory() throws Exception {
    return temporaryFolder.getRoot().getAbsolutePath();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void write(String path, String content) throws Exception {
    sftpClient.write(path, new ByteArrayInputStream(content.getBytes()), APPEND);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean dirExists(String path) throws Exception {
    FileAttributes attributes = sftpClient.getAttributes(Paths.get(path));
    return attributes != null && attributes.isDirectory();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean fileExists(String path) throws Exception {
    return sftpClient.getAttributes(Paths.get(path)) != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean changeWorkingDirectory(String path) throws Exception {
    try {
      sftpClient.changeWorkingDirectory(path);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] getFileList(String path) throws Exception {
    List<String> files = sftpClient.list(path).stream().map(FileAttributes::getName).collect(toList());
    return files.toArray(new String[files.size()]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getServerPort() throws Exception {
    return sftpPort.getNumber();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void assertAttributes(String path, Object attributes) throws Exception {
    SftpFileAttributes fileAttributes = (SftpFileAttributes) attributes;
    SftpFileAttributes file = sftpClient.getAttributes(Paths.get(path));

    assertThat(fileAttributes.getName(), equalTo(file.getName()));
    assertTrue(Paths.get(temporaryFolder.getRoot().getPath(), HELLO_PATH).toString()
        .contains(Paths.get(fileAttributes.getPath()).toString()));
    assertThat(fileAttributes.getSize(), is(file.getSize()));
    assertThat(fileAttributes.getTimestamp(), equalTo(file.getTimestamp()));
    assertThat(fileAttributes.isDirectory(), is(false));
    assertThat(fileAttributes.isSymbolicLink(), is(false));
    assertThat(fileAttributes.isRegularFile(), is(true));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void assertDeleted(String path) throws Exception {
    Path directoryPath = temporaryFolder.getRoot().toPath().resolve(path);
    int lastFragmentIndex = directoryPath.getNameCount() - 1;

    Path lastFragment = directoryPath.getName(lastFragmentIndex);
    if (".".equals(lastFragment.toString())) {
      directoryPath = Paths.get("/").resolve(directoryPath.subpath(0, lastFragmentIndex)).toAbsolutePath();
    }

    assertThat(dirExists(directoryPath.toString()), is(false));
  }

  public enum AuthType {
    USER_PASSWORD, PUBLIC_KEY
  }

  @FunctionalInterface
  private interface AuthConfigurator<T> {

    void configure(T sftpClient);
  }
}

/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.sftp;

import static java.nio.charset.Charset.availableCharsets;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mule.extension.file.common.api.FileWriteMode.APPEND;
import static org.mule.extension.file.common.api.FileWriteMode.CREATE_NEW;
import static org.mule.extension.file.common.api.FileWriteMode.OVERWRITE;
import static org.mule.extension.file.common.api.exceptions.FileError.FILE_ALREADY_EXISTS;
import static org.mule.extension.file.common.api.exceptions.FileError.ILLEGAL_PATH;
import static org.mule.extension.sftp.AllureConstants.SftpFeature.SFTP_EXTENSION;
import static org.mule.runtime.core.api.util.IOUtils.toByteArray;
import static org.mule.test.extension.file.common.api.FileTestHarness.HELLO_WORLD;
import org.mule.extension.file.common.api.FileWriteMode;
import org.mule.extension.file.common.api.exceptions.FileAlreadyExistsException;
import org.mule.extension.file.common.api.exceptions.IllegalPathException;
import org.mule.runtime.core.api.event.CoreEvent;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;

import io.qameta.allure.Feature;
import org.junit.Test;

@Feature(SFTP_EXTENSION)
public class SftpWriteTestCase extends CommonSftpConnectorTestCase {

  private static final String TEMP_DIRECTORY = "files";

  public SftpWriteTestCase(String name, SftpTestHarness testHarness, String ftpConfigFile) {
    super(name, testHarness, ftpConfigFile);
  }

  @Override
  protected String getConfigFile() {
    return "sftp-write-config.xml";
  }

  @Test
  public void appendOnNotExistingFile() throws Exception {
    doWriteOnNotExistingFile(APPEND);
  }

  @Test
  public void overwriteOnNotExistingFile() throws Exception {
    doWriteOnNotExistingFile(OVERWRITE);
  }

  @Test
  public void createNewOnNotExistingFile() throws Exception {
    doWriteOnNotExistingFile(CREATE_NEW);
  }

  @Test
  public void appendOnExistingFile() throws Exception {
    String content = doWriteOnExistingFile(APPEND);
    assertThat(content, is(HELLO_WORLD + HELLO_WORLD));
  }

  @Test
  public void overwriteOnExistingFile() throws Exception {
    String content = doWriteOnExistingFile(OVERWRITE);
    assertThat(content, is(HELLO_WORLD));
  }

  @Test
  public void createNewOnExistingFile() throws Exception {
    testHarness.expectedError().expectError(NAMESPACE, FILE_ALREADY_EXISTS.getType(), FileAlreadyExistsException.class,
                                            "Use a different write mode or point to a path which doesn't exist");
    doWriteOnExistingFile(CREATE_NEW);
  }

  @Test
  public void appendOnNotExistingParentWithoutCreateFolder() throws Exception {
    testHarness.expectedError().expectError(NAMESPACE, ILLEGAL_PATH.getType(), IllegalPathException.class,
                                            "because path to it doesn't exist");
    doWriteOnNotExistingParentWithoutCreateFolder(APPEND);
  }

  @Test
  public void overwriteOnNotExistingParentWithoutCreateFolder() throws Exception {
    testHarness.expectedError().expectError(NAMESPACE, ILLEGAL_PATH.getType(), IllegalPathException.class,
                                            "because path to it doesn't exist");
    doWriteOnNotExistingParentWithoutCreateFolder(OVERWRITE);
  }

  @Test
  public void createNewOnNotExistingParentWithoutCreateFolder() throws Exception {
    testHarness.expectedError().expectError(NAMESPACE, ILLEGAL_PATH.getType(), IllegalPathException.class,
                                            "because path to it doesn't exist");
    doWriteOnNotExistingParentWithoutCreateFolder(CREATE_NEW);
  }

  @Test
  public void appendNotExistingFileWithCreatedParent() throws Exception {
    doWriteNotExistingFileWithCreatedParent(APPEND);
  }

  @Test
  public void overwriteNotExistingFileWithCreatedParent() throws Exception {
    doWriteNotExistingFileWithCreatedParent(OVERWRITE);
  }

  @Test
  public void createNewNotExistingFileWithCreatedParent() throws Exception {
    doWriteNotExistingFileWithCreatedParent(CREATE_NEW);
  }

  @Test
  public void writeOnReadFile() throws Exception {
    final String filePath = "file";

    testHarness.write(filePath, "overwrite me!");

    CoreEvent event = flowRunner("readAndWrite").withVariable("path", filePath).run();

    assertThat(event.getMessage().getPayload().getValue(), equalTo(HELLO_WORLD));
  }

  @Test
  public void writeStaticContent() throws Exception {
    testHarness.makeDir(TEMP_DIRECTORY);
    String path = Paths.get(testHarness.getWorkingDirectory(), TEMP_DIRECTORY, "test.txt").toString();
    doWrite("writeStaticContent", path, "", CREATE_NEW, false);

    String content = toString(readPath(path).getPayload().getValue());
    assertThat(content, is(HELLO_WORLD));
  }

  @Test
  public void writeWithCustomEncoding() throws Exception {
    final String defaultEncoding = muleContext.getConfiguration().getDefaultEncoding();
    assertThat(defaultEncoding, is(notNullValue()));

    final String customEncoding =
        availableCharsets().keySet().stream().filter(encoding -> !encoding.equals(defaultEncoding)).findFirst().orElse(null);

    assertThat(customEncoding, is(notNullValue()));
    final String filename = "encoding.txt";

    doWrite("write", filename, HELLO_WORLD, CREATE_NEW, false, customEncoding);
    String path = Paths.get(testHarness.getWorkingDirectory()).resolve(filename).toString();
    InputStream content = (InputStream) readPath(path, false).getPayload().getValue();

    assertThat(Arrays.equals(toByteArray(content), HELLO_WORLD.getBytes(customEncoding)), is(true));
  }

  private void doWriteNotExistingFileWithCreatedParent(FileWriteMode mode) throws Exception {
    testHarness.makeDir(TEMP_DIRECTORY);
    String path = Paths.get(testHarness.getWorkingDirectory(), TEMP_DIRECTORY, "a/b/test.txt").toString();

    doWrite(path, HELLO_WORLD, mode, true);

    String content = toString(readPath(path).getPayload().getValue());
    assertThat(content, is(HELLO_WORLD));
  }


  private void doWriteOnNotExistingFile(FileWriteMode mode) throws Exception {
    testHarness.makeDir(TEMP_DIRECTORY);
    String path = Paths.get(testHarness.getWorkingDirectory(), TEMP_DIRECTORY, "test.txt").toString();
    doWrite(path, HELLO_WORLD, mode, false);

    String content = toString(readPath(path));
    assertThat(content, is(HELLO_WORLD));
  }

  private void doWriteOnNotExistingParentWithoutCreateFolder(FileWriteMode mode) throws Exception {
    testHarness.makeDir(TEMP_DIRECTORY);
    String path = Paths.get(testHarness.getWorkingDirectory(), TEMP_DIRECTORY, "a/b/test.txt").toString();
    doWrite(path, HELLO_WORLD, mode, false);
  }

  private String doWriteOnExistingFile(FileWriteMode mode) throws Exception {
    final String filePath = "file";
    testHarness.write(filePath, HELLO_WORLD);

    doWrite(filePath, HELLO_WORLD, mode, false);
    return toString(readPath(filePath).getPayload().getValue());
  }
}

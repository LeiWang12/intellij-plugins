package com.intellij.lang.javascript.flex.run;

import com.intellij.CommonBundle;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.browsers.BrowsersConfiguration;
import com.intellij.lang.javascript.flex.FlexBundle;
import com.intellij.lang.javascript.flex.FlexStackTraceFilter;
import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.lang.javascript.flex.actions.airpackage.AirPackageUtil;
import com.intellij.lang.javascript.flex.build.FlexCompilationUtils;
import com.intellij.lang.javascript.flex.debug.FlexDebugProcess;
import com.intellij.lang.javascript.flex.debug.FlexDebugRunner;
import com.intellij.lang.javascript.flex.flexunit.FlexUnitConsoleProperties;
import com.intellij.lang.javascript.flex.flexunit.FlexUnitRunConfiguration;
import com.intellij.lang.javascript.flex.flexunit.FlexUnitRunnerParameters;
import com.intellij.lang.javascript.flex.projectStructure.FlexBuildConfigurationsExtension;
import com.intellij.lang.javascript.flex.projectStructure.model.AirDesktopPackagingOptions;
import com.intellij.lang.javascript.flex.projectStructure.model.AirPackagingOptions;
import com.intellij.lang.javascript.flex.projectStructure.model.FlexIdeBuildConfiguration;
import com.intellij.lang.javascript.flex.projectStructure.model.TargetPlatform;
import com.intellij.lang.javascript.flex.projectStructure.options.BCUtils;
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
import com.intellij.notification.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.navigation.Place;
import com.intellij.util.PathUtil;
import com.intellij.xdebugger.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import static com.intellij.lang.javascript.flex.run.FlashRunnerParameters.AppDescriptorForEmulator;

/**
 * User: Maxim.Mossienko
 * Date: Mar 11, 2008
 * Time: 8:16:33 PM
 */
public abstract class FlexBaseRunner extends GenericProgramRunner {

  public static final NotificationGroup COMPILE_BEFORE_LAUNCH_NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup(
    FlexBundle.message("check.flash.app.compiled.before.launch.notification.group"), ToolWindowId.RUN, false);

  public static final RunProfileState EMPTY_RUN_STATE = new RunProfileState() {
    public ExecutionResult execute(final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
      return null;
    }

    public RunnerSettings getRunnerSettings() {
      return null;
    }

    public ConfigurationPerRunnerSettings getConfigurationSettings() {
      return null;
    }
  };

  @Nullable
  protected RunContentDescriptor doExecute(final Project project,
                                           final Executor executor,
                                           final RunProfileState state,
                                           final RunContentDescriptor contentToReuse,
                                           final ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    final RunProfile runProfile = env.getRunProfile();

    final boolean isDebug = this instanceof FlexDebugRunner;
    try {
      if (runProfile instanceof RunProfileWithCompileBeforeLaunchOption) {
        checkMakeBeforeRunEnabled(project, runProfile, isDebug);
      }

      if (runProfile instanceof RemoteFlashRunConfiguration) {
        final BCBasedRunnerParameters params = ((RemoteFlashRunConfiguration)runProfile).getRunnerParameters();
        final Pair<Module, FlexIdeBuildConfiguration> moduleAndBC = params.checkAndGetModuleAndBC(project);
        return launchDebugProcess(moduleAndBC.first, moduleAndBC.second, params, executor, contentToReuse, env);
      }

      if (runProfile instanceof FlexUnitRunConfiguration) {
        final FlexUnitRunnerParameters params = ((FlexUnitRunConfiguration)runProfile).getRunnerParameters();
        final Pair<Module, FlexIdeBuildConfiguration> moduleAndConfig = params.checkAndGetModuleAndBC(project);
        final Module module = moduleAndConfig.first;
        final FlexIdeBuildConfiguration bc = moduleAndConfig.second;

        if (bc.getTargetPlatform() == TargetPlatform.Web) {
          final String outputFilePath = bc.getActualOutputFilePath();
          try {
            final String canonicalPath = new File(PathUtil.getParentPath(outputFilePath)).getCanonicalPath();
            FlashPlayerTrustUtil.updateTrustedStatus(module.getProject(), params.isTrusted(), false, canonicalPath);
          }
          catch (IOException e) {/**/}

          return launchWebFlexUnit(project, executor, contentToReuse, env, params, outputFilePath);
        }
        else {
          return launchAirFlexUnit(project, executor, state, contentToReuse, env, params);
        }
      }

      if (runProfile instanceof FlashRunConfiguration) {
        final FlashRunnerParameters params = ((FlashRunConfiguration)runProfile).getRunnerParameters();
        final Pair<Module, FlexIdeBuildConfiguration> moduleAndConfig = params.checkAndGetModuleAndBC(project);
        final Module module = moduleAndConfig.first;
        final FlexIdeBuildConfiguration bc = moduleAndConfig.second;
        if (bc.isSkipCompile()) {
          showBCCompilationSkippedWarning(module, bc, isDebug);
        }

        if (isDebug && SystemInfo.isMac && bc.getTargetPlatform() == TargetPlatform.Web) {
          checkDebuggerFromSdk4(project, runProfile, params, bc);
        }

        if (bc.getTargetPlatform() == TargetPlatform.Web && !params.isLaunchUrl()) {
          try {
            final String canonicalPath = new File(PathUtil.getParentPath(bc.getActualOutputFilePath())).getCanonicalPath();
            FlashPlayerTrustUtil.updateTrustedStatus(module.getProject(), params.isRunTrusted(), isDebug, canonicalPath);
          }
          catch (IOException e) {/**/}
        }

        return launchFlexIdeConfig(module, bc, params, executor, state, contentToReuse, env);
      }
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e.getMessage());
    }

    return null;
  }

  protected RunContentDescriptor launchDebugProcess(final Module module,
                                                    final FlexIdeBuildConfiguration bc,
                                                    final BCBasedRunnerParameters params,
                                                    final Executor executor,
                                                    final RunContentDescriptor contentToReuse,
                                                    final ExecutionEnvironment env) throws ExecutionException {
    final XDebugSession debugSession =
      XDebuggerManager.getInstance(module.getProject()).startSession(this, env, contentToReuse, new XDebugProcessStarter() {
        @NotNull
        public XDebugProcess start(@NotNull final XDebugSession session) throws ExecutionException {
          try {
            if (params instanceof FlexUnitRunnerParameters) {
              return new FlexDebugProcess(session, bc, params) {
                @NotNull
                @Override
                public ExecutionConsole createConsole() {
                  try {
                    return createFlexUnitRunnerConsole(session.getProject(), env, getProcessHandler(), executor);
                  }
                  catch (ExecutionException e) {
                    Logger.getInstance(FlexBaseRunner.class.getName()).error(e);
                  }
                  return super.createConsole();
                }
              };
            }
            return new FlexDebugProcess(session, bc, params);
          }
          catch (IOException e) {
            iosStopForwardTcpPortIfNeeded(module.getProject(), bc, params);
            throw new ExecutionException(e.getMessage(), e);
          }
        }
      });

    debugSession.addSessionListener(new XDebugSessionAdapter() {
      public void sessionStopped() {
        iosStopForwardTcpPortIfNeeded(module.getProject(), bc, params);
      }
    });

    return debugSession.getRunContentDescriptor();
  }

  private static void iosStopForwardTcpPortIfNeeded(final Project project, final FlexIdeBuildConfiguration bc,
                                                    final BCBasedRunnerParameters params) {
    if (bc.getTargetPlatform() == TargetPlatform.Mobile &&
        params instanceof FlashRunnerParameters &&
        ((FlashRunnerParameters)params).getMobileRunTarget() == FlashRunnerParameters.AirMobileRunTarget.iOSDevice &&
        ((FlashRunnerParameters)params).getDebugTransport() == FlashRunnerParameters.AirMobileDebugTransport.USB) {
      AirPackageUtil.iosStopForwardTcpPort(project, bc.getSdk(), ((FlashRunnerParameters)params).getUsbDebugPort());
    }
  }

  @Nullable
  protected abstract RunContentDescriptor launchAirFlexUnit(final Project project,
                                                            final Executor executor,
                                                            final RunProfileState state,
                                                            final RunContentDescriptor contentToReuse,
                                                            final ExecutionEnvironment env,
                                                            final FlexUnitRunnerParameters params) throws ExecutionException;

  protected abstract RunContentDescriptor launchWebFlexUnit(final Project project,
                                                            final Executor executor,
                                                            final RunContentDescriptor contentToReuse,
                                                            final ExecutionEnvironment env,
                                                            final FlexUnitRunnerParameters params,
                                                            final String swfFilePath) throws ExecutionException;

  @Nullable
  protected abstract RunContentDescriptor launchFlexIdeConfig(final Module module,
                                                              final FlexIdeBuildConfiguration config,
                                                              final FlashRunnerParameters params,
                                                              final Executor executor,
                                                              final RunProfileState state,
                                                              final RunContentDescriptor contentToReuse,
                                                              final ExecutionEnvironment environment) throws ExecutionException;

  public static ExecutionConsole createFlexUnitRunnerConsole(Project project,
                                                             ExecutionEnvironment env,
                                                             ProcessHandler processHandler,
                                                             Executor executor) throws ExecutionException {
    final RunProfile runConfiguration = env.getRunProfile();
    final FlexStackTraceFilter stackTraceFilter = new FlexStackTraceFilter(project);
    final FlexUnitConsoleProperties consoleProps = new FlexUnitConsoleProperties(((FlexUnitRunConfiguration)runConfiguration), executor);
    consoleProps.addStackTraceFilter(stackTraceFilter);

    final BaseTestsOutputConsoleView consoleView = SMTestRunnerConnectionUtil
      .createAndAttachConsole("FlexUnit", processHandler, consoleProps, env.getRunnerSettings(), env.getConfigurationSettings());
    consoleView.addMessageFilter(stackTraceFilter);
    Disposer.register(project, consoleView);
    return consoleView;
  }

  public static void launchWithSelectedApplication(final String urlOrPath, final LauncherParameters launcherParams) {
    switch (launcherParams.getLauncherType()) {
      case OSDefault:
        if (SystemInfo.isWindows) {
          // workaround of http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6457572
          try {
            Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "start", "\"\"", urlOrPath});
            break;
          }
          catch (IOException ignored) {/*ignored*/}
        }

        if (Desktop.isDesktopSupported()) {
          final Desktop desktop = Desktop.getDesktop();
          if (BrowserUtil.isAbsoluteURL(urlOrPath)) {
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
              try {
                desktop.browse(BrowserUtil.getURL(urlOrPath).toURI());
                break;
              }
              catch (IOException ignored) {/*ignored*/}
              catch (URISyntaxException ignored) {/*ignored*/}
            }
          }
          else {
            if (desktop.isSupported(Desktop.Action.OPEN)) {
              try {
                desktop.open(new File(urlOrPath));
                break;
              }
              catch (IOException ignored) {/*ignored*/}
              catch (IllegalArgumentException ignored) {/*ignored*/}
            }
          }
        }

        BrowserUtil.launchBrowser(urlOrPath);
        break;

      case Browser:
        final Runnable runnable1 = new Runnable() {
          public void run() {
            BrowsersConfiguration.launchBrowser(launcherParams.getBrowserFamily(),
                                                BrowserUtil.isAbsoluteURL(urlOrPath) ? urlOrPath : VfsUtil.pathToUrl(urlOrPath));
          }
        };

        final Application application1 = ApplicationManager.getApplication();
        if (application1.isDispatchThread()) {
          runnable1.run();
        }
        else {
          application1.invokeLater(runnable1);
        }
        break;

      case Player:
        final String playerPath = launcherParams.getPlayerPath();
        final String executablePath = SystemInfo.isMac && playerPath.endsWith(".app") ? FlexUtils.getMacExecutable(playerPath) : playerPath;
        try {
          if (executablePath == null) {
            throw new IOException("failed to find application to launch with");
          }

          Runtime.getRuntime().exec(new String[]{executablePath, urlOrPath});
        }
        catch (final IOException e) {
          final Runnable runnable2 = new Runnable() {
            public void run() {
              Messages.showErrorDialog(FlexBundle.message("cant.launch", urlOrPath, playerPath, e.getMessage()),
                                       CommonBundle.getErrorTitle());
            }
          };

          final Application application2 = ApplicationManager.getApplication();
          if (application2.isDispatchThread()) {
            runnable2.run();
          }
          else {
            application2.invokeLater(runnable2);
          }
        }
        break;
    }
  }

  public static boolean packAndInstallToAndroidDevice(final Module module,
                                                      final FlexIdeBuildConfiguration bc,
                                                      final FlashRunnerParameters runnerParameters,
                                                      final String applicationId,
                                                      final boolean isDebug) {
    final Project project = module.getProject();
    final Sdk sdk = bc.getSdk();
    final String outputFolder = PathUtil.getParentPath(bc.getActualOutputFilePath());
    final String apkPath = outputFolder + "/" + bc.getAndroidPackagingOptions().getPackageFileName() + ".apk";

    final String adtVersion;
    return (adtVersion = AirPackageUtil.getAdtVersion(project, sdk)) != null
           && AirPackageUtil.checkAdtVersion(module, bc, adtVersion)
           && AirPackageUtil.startAdbServer(project, sdk)
           && AirPackageUtil.checkAirRuntimeOnDevice(project, sdk, adtVersion)
           && AirPackageUtil.packageApk(module, bc, runnerParameters, isDebug)
           && AirPackageUtil.installApk(project, sdk, apkPath, applicationId);
  }

  public static boolean packAndInstallToIOSSimulator(final Module module,
                                                     final FlexIdeBuildConfiguration bc,
                                                     final FlashRunnerParameters runnerParameters,
                                                     final String applicationId,
                                                     final boolean isDebug) {
    final Project project = module.getProject();
    final Sdk sdk = bc.getSdk();
    final String outputFolder = PathUtil.getParentPath(bc.getActualOutputFilePath());
    final String ipaPath = outputFolder + "/" + bc.getIosPackagingOptions().getPackageFileName() + ".ipa";

    // todo check apk version (>=3.3), use temporary cert if needed
    return (AirPackageUtil.packageIpaForSimulator(module, bc, runnerParameters, isDebug)
            && AirPackageUtil.installOnIosSimulator(project, sdk, ipaPath, applicationId, runnerParameters.getIOSSimulatorSdkPath()));
  }

  public static boolean packAndInstallToIOSDevice(final Module module,
                                                  final FlexIdeBuildConfiguration bc,
                                                  final FlashRunnerParameters runnerParameters,
                                                  final boolean isDebug) {
    final Project project = module.getProject();
    final Sdk sdk = bc.getSdk();
    final String outputFolder = PathUtil.getParentPath(bc.getActualOutputFilePath());
    final String ipaPath = outputFolder + "/" + bc.getIosPackagingOptions().getPackageFileName() + ".ipa";

    final String adtVersion;
    return (adtVersion = AirPackageUtil.getAdtVersion(project, sdk)) != null
           && AirPackageUtil.checkAdtVersion(module, bc, adtVersion)
           && AirPackageUtil.packageIpaForDevice(module, bc, runnerParameters, isDebug)
           && AirPackageUtil.installOnIosDevice(project, sdk, ipaPath);
  }

  @Nullable
  public static String getApplicationId(final String airDescriptorPath) {
    final VirtualFile descriptorFile = LocalFileSystem.getInstance().findFileByPath(airDescriptorPath);
    if (descriptorFile != null) {
      try {
        return FlexUtils.findXMLElement(descriptorFile.getInputStream(), "<application><id>");
      }
      catch (IOException e) {/*ignore*/}
    }
    return null;
  }

  @Nullable
  public static String getApplicationName(final String airDescriptorPath) {
    final VirtualFile descriptorFile = LocalFileSystem.getInstance().findFileByPath(airDescriptorPath);
    if (descriptorFile != null) {
      try {
        return FlexUtils.findXMLElement(descriptorFile.getInputStream(), "<application><name>");
      }
      catch (IOException e) {/*ignore*/}
    }
    return null;
  }

  public static void launchOnAndroidDevice(final Project project, final Sdk flexSdk, final String applicationId, final boolean isDebug) {
    if (AirPackageUtil.launchAndroidApplication(project, flexSdk, applicationId)) {
      ToolWindowManager.getInstance(project).notifyByBalloon(isDebug ? ToolWindowId.DEBUG : ToolWindowId.RUN, MessageType.INFO,
                                                             FlexBundle.message("android.application.launched"));
    }
  }

  public static void launchOnIosSimulator(final Project project,
                                          final Sdk flexSdk,
                                          final String applicationId,
                                          final String iOSSdkPath,
                                          final boolean isDebug) {
    if (AirPackageUtil.launchOnIosSimulator(project, flexSdk, applicationId, iOSSdkPath)) {
      ToolWindowManager.getInstance(project).notifyByBalloon(isDebug ? ToolWindowId.DEBUG : ToolWindowId.RUN, MessageType.INFO,
                                                             FlexBundle.message("ios.simulator.application.launched"));
    }
  }

  public static GeneralCommandLine createAdlCommandLine(final Project project,
                                                        final BCBasedRunnerParameters params,
                                                        final FlexIdeBuildConfiguration bc,
                                                        final @Nullable String airRuntimePath) throws CantRunException {
    final Module module = ModuleManager.getInstance(project).findModuleByName(params.getModuleName());
    final Sdk sdk = bc.getSdk();

    if (module == null) {
      throw new CantRunException(FlexBundle.message("module.not.found", params.getModuleName()));
    }
    if (sdk == null) {
      throw new CantRunException(FlexBundle.message("sdk.not.set.for.bc.0.of.module.1", bc.getName(), params.getModuleName()));
    }

    final GeneralCommandLine commandLine = new GeneralCommandLine();

    commandLine.setExePath(FileUtil.toSystemDependentName(FlexSdkUtils.getAdlPath(sdk)));

    if (airRuntimePath != null) {
      commandLine.addParameter("-runtime");
      commandLine.addParameter(airRuntimePath);
    }

    final Collection<VirtualFile> aneFiles = FlexCompilationUtils.getANEFiles(ModuleRootManager.getInstance(module), bc.getDependencies());
    if (!aneFiles.isEmpty()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          if (indicator != null) indicator.setIndeterminate(true);
          FlexCompilationUtils.unzipANEFiles(aneFiles, indicator);
        }
      }, "Unzipping ANE files", true, project);
    }

    if (bc.getNature().isDesktopPlatform()) {
      final String adlOptions =
        params instanceof FlashRunnerParameters ? ((FlashRunnerParameters)params).getAdlOptions() : "";

      if (StringUtil.compareVersionNumbers(sdk.getVersionString(), "4") >= 0 &&
          FlexUtils.getOptionValues(adlOptions, "profile").isEmpty()) {
        commandLine.addParameter("-profile");
        commandLine.addParameter("extendedDesktop");
      }

      if (!StringUtil.isEmptyOrSpaces(adlOptions)) {
        for (StringTokenizer tokenizer = new CommandLineTokenizer(adlOptions); tokenizer.hasMoreTokens(); ) {
          commandLine.addParameter(tokenizer.nextToken());
        }
      }

      if (!aneFiles.isEmpty()) {
        commandLine.addParameter("-extdir");
        commandLine.addParameter(FlexCompilationUtils.getPathToUnzipANE());
      }

      final AirDesktopPackagingOptions packagingOptions = bc.getAirDesktopPackagingOptions();
      commandLine.addParameter(FileUtil.toSystemDependentName(getAirDescriptorPath(bc, packagingOptions)));
      commandLine.addParameter(FileUtil.toSystemDependentName(PathUtil.getParentPath(bc.getActualOutputFilePath())));
      final String programParameters =
        params instanceof FlashRunnerParameters ? ((FlashRunnerParameters)params).getAirProgramParameters() : "";
      if (!StringUtil.isEmptyOrSpaces(programParameters)) {
        commandLine.addParameter("--");
        for (StringTokenizer tokenizer = new CommandLineTokenizer(programParameters); tokenizer.hasMoreTokens(); ) {
          commandLine.addParameter(tokenizer.nextToken());
        }
      }
    }
    else {
      assert params instanceof FlashRunnerParameters;
      final FlashRunnerParameters flashParams = (FlashRunnerParameters)params;
      assert bc.getNature().isMobilePlatform() : bc.getTargetPlatform();
      assert flashParams.getMobileRunTarget() == FlashRunnerParameters.AirMobileRunTarget.Emulator : flashParams.getMobileRunTarget();

      final String adlOptions = flashParams.getEmulatorAdlOptions();

      if (FlexUtils.getOptionValues(adlOptions, "profile").isEmpty()) {
        commandLine.addParameter("-profile");
        commandLine.addParameter("extendedMobileDevice");
      }

      final FlashRunnerParameters.Emulator emulator = flashParams.getEmulator();
      final boolean customSize = emulator.adlAlias == null;

      commandLine.addParameter("-screensize");
      if (customSize) {
        commandLine.addParameter(flashParams.getScreenWidth() + "x" + flashParams.getScreenHeight() +
                                 ":" + flashParams.getFullScreenWidth() + "x" + flashParams.getFullScreenHeight());
      }
      else {
        commandLine.addParameter(emulator.adlAlias);
      }

      if (FlexUtils.getOptionValues(adlOptions, "XscreenDPI").isEmpty()) {
        if (customSize && flashParams.getScreenDpi() > 0) {
          commandLine.addParameter("-XscreenDPI");
          commandLine.addParameter(String.valueOf(flashParams.getScreenDpi()));
        }
        else if (!customSize && emulator.screenDPI > 0) {
          commandLine.addParameter("-XscreenDPI");
          commandLine.addParameter(String.valueOf(emulator.screenDPI));
        }
      }

      if (FlexUtils.getOptionValues(adlOptions, "XversionPlatform").isEmpty() && emulator.versionPlatform != null) {
        commandLine.addParameter("-XversionPlatform");
        commandLine.addParameter(emulator.versionPlatform);
      }

      if (!StringUtil.isEmptyOrSpaces(adlOptions)) {
        for (StringTokenizer tokenizer = new CommandLineTokenizer(adlOptions); tokenizer.hasMoreTokens(); ) {
          commandLine.addParameter(tokenizer.nextToken());
        }
      }

      if (!aneFiles.isEmpty()) {
        commandLine.addParameter("-extdir");
        commandLine.addParameter(FlexCompilationUtils.getPathToUnzipANE());
      }

      final String airDescriptorPath = getDescriptorForEmulatorPath(module, bc, flashParams.getAppDescriptorForEmulator());
      commandLine.addParameter(FileUtil.toSystemDependentName(airDescriptorPath));
      commandLine.addParameter(FileUtil.toSystemDependentName(PathUtil.getParentPath(bc.getActualOutputFilePath())));
    }

    return commandLine;
  }

  private static String getDescriptorForEmulatorPath(final Module module, final FlexIdeBuildConfiguration bc,
                                                     final AppDescriptorForEmulator appDescriptorForEmulator) throws CantRunException {
    final String airDescriptorPath;
    switch (appDescriptorForEmulator) {
      case Android:
        airDescriptorPath = getAirDescriptorPath(bc, bc.getAndroidPackagingOptions());
        break;
      case IOS:
        airDescriptorPath = getAirDescriptorPath(bc, bc.getIosPackagingOptions());
        break;
      default:
        assert false;
        airDescriptorPath = "";
    }
    return airDescriptorPath;
  }

  public static String getAirDescriptorPath(final FlexIdeBuildConfiguration bc, final AirPackagingOptions packagingOptions) {
    return PathUtil.getParentPath(bc.getActualOutputFilePath()) + "/" + getAirDescriptorFileName(bc, packagingOptions);
  }

  private static String getAirDescriptorFileName(final FlexIdeBuildConfiguration bc, final AirPackagingOptions packagingOptions) {
    return packagingOptions.isUseGeneratedDescriptor() ? BCUtils.getGeneratedAirDescriptorName(bc, packagingOptions)
                                                       : PathUtil.getFileName(packagingOptions.getCustomDescriptorPath());
  }

  private static void checkMakeBeforeRunEnabled(final Project project, final RunProfile runProfile, final boolean debug) {
    final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    final List<CompileStepBeforeRun.MakeBeforeRunTask> makeBeforeRunTasks =
      runManager.getBeforeRunTasks((RunConfiguration)runProfile, CompileStepBeforeRun.ID);
    if (makeBeforeRunTasks.isEmpty()) {
      for (RunnerAndConfigurationSettings settings : runManager.getConfigurationSettings(((RunConfiguration)runProfile).getType())) {
        if (settings.getConfiguration() == runProfile) {
          showMakeBeforeRunTurnedOffWarning(project, settings, debug);
          break;
        }
      }
    }
  }

  private static void showMakeBeforeRunTurnedOffWarning(final Project project,
                                                        final RunnerAndConfigurationSettings configuration,
                                                        final boolean isDebug) {
    final String message = FlexBundle.message("run.when.compile.before.run.turned.off");
    COMPILE_BEFORE_LAUNCH_NOTIFICATION_GROUP.createNotification("", message, NotificationType.WARNING, new NotificationListener() {
      public void hyperlinkUpdate(@NotNull final Notification notification, @NotNull final HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          notification.expire();

          if ("RunConfiguration".equals(event.getDescription())) {
            RunDialog.editConfiguration(project, configuration, FlexBundle.message("edit.configuration.title"));
          }
          else if ("DisableWarning".equals(event.getDescription())) {
            disableCompilationSkippedWarning(project);
          }
        }
      }
    }).notify(project);
  }

  private static void showBCCompilationSkippedWarning(final Module module, final FlexIdeBuildConfiguration bc, final boolean isDebug) {
    final String message = FlexBundle.message("run.when.ide.builder.turned.off", bc.getName(), module.getName());
    COMPILE_BEFORE_LAUNCH_NOTIFICATION_GROUP.createNotification("", message, NotificationType.WARNING, new NotificationListener() {
      public void hyperlinkUpdate(@NotNull final Notification notification, @NotNull final HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          notification.expire();

          if ("BuildConfiguration".equals(event.getDescription())) {
            final ProjectStructureConfigurable projectStructureConfigurable = ProjectStructureConfigurable.getInstance(module.getProject());
            ShowSettingsUtil.getInstance().editConfigurable(module.getProject(), projectStructureConfigurable, new Runnable() {
              public void run() {
                Place p = FlexBuildConfigurationsExtension.getInstance().getConfigurator().getPlaceFor(module, bc.getName());
                projectStructureConfigurable.navigateTo(p, true);
              }
            });
          }
          else if ("DisableWarning".equals(event.getDescription())) {
            disableCompilationSkippedWarning(module.getProject());
          }
        }
      }
    }).notify(module.getProject());
  }

  private static void disableCompilationSkippedWarning(final Project project) {
    NotificationsConfiguration.getNotificationsConfiguration()
      .changeSettings(COMPILE_BEFORE_LAUNCH_NOTIFICATION_GROUP.getDisplayId(), NotificationDisplayType.NONE, false);
    ToolWindowManager.getInstance(project)
      .notifyByBalloon(EventLog.LOG_TOOL_WINDOW_ID, MessageType.INFO, FlexBundle.message("make.before.launch.warning.disabled"));
  }

  private static void checkDebuggerFromSdk4(final Project project,
                                            final RunProfile runProfile,
                                            final FlashRunnerParameters params,
                                            final FlexIdeBuildConfiguration bc) {
    final Sdk sdk = bc.getSdk();
    assert sdk != null;

    final Sdk sdkForDebugger = FlexDebugProcess.getDebuggerSdk(params.getDebuggerSdkRaw(), sdk);
    if (StringUtil.compareVersionNumbers(sdkForDebugger.getVersionString(), "4") < 0) {
      final HyperlinkListener listener = new HyperlinkAdapter() {
        protected void hyperlinkActivated(final HyperlinkEvent e) {
          if ("RunConfiguration".equals(e.getDescription())) {
            final RunnerAndConfigurationSettings[] allConfigurations =
              RunManagerEx.getInstanceEx(project).getConfigurationSettings(((RunConfiguration)runProfile).getType());
            for (RunnerAndConfigurationSettings configuration : allConfigurations) {
              if (configuration.getConfiguration() == runProfile) {
                RunDialog.editConfiguration(project, configuration, FlexBundle.message("edit.configuration.title"));
                break;
              }
            }
          }
        }
      };
      final String message = FlexBundle.message("flex.sdk.3.mac.debug.problem", sdkForDebugger.getVersionString());
      ToolWindowManager.getInstance(project).notifyByBalloon(ToolWindowId.DEBUG, MessageType.WARNING, message, null, listener);
    }
  }
}

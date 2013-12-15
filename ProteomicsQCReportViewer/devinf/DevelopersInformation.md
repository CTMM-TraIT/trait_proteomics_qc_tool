Developers information
======================

This page is meant for developers that want to change something about the Proteomics QC tool, send a patch with a bug fix or an improvement, or are interested in finding out more about the development process in general. The Proteomics QC tool consists of two main parts: a modified version of the [NIST MSQC pipeline](http://peptide.nist.gov/software/nist_msqc_pipeline/NIST_MSQC_Pipeline.html) and the Proteomics QC viewer, which provides a GUI for analyzing the QC reports produced by the pipeline.

We currently have some information on using git & GitHub (for code management), Maven (for building the code), Checkstyle (code style checker) and FindBugs (static code analysis tool).


Using git and GitHub
--------------------

We are using [git](http://git-scm.com/) and [GitHub](https://github.com/) to manage the code of the Proteomics QC tool. Git is a free and open source distributed version control system, which makes it easy to work with a team on a collection of (source code) files. GitHub is a web-based hosting service for software development projects that use the git version control system (see [GitHub on Wikipedia](http://en.wikipedia.org/wiki/GitHub) for more information).

If you want to change the files in a GitHub repository, the common approach is to create a fork: a copy of the master repository that is linked to from the forked one (see [Fork A Repo on the GitHub web site](https://help.github.com/articles/fork-a-repo)). Once you have created your own fork, you can commit and push changes to that repository. Then you can create a pull request asking the maintainers of the master repository to accept your changes.

Changes that are made to the master repository by your team members do not show up automatically in your forked repository. You can fetch and merge their work using the following commands (on the command-line):

\# Fetch any new changes from the original master repository:<br/>
**`git fetch upstream`**

\# Merge any changes fetched into your working files:<br/>
**`git merge upstream/master`**

Merging sometimes leads to conflicts, which gives a message like this:<br/>
"Updating 123a456..c789e00<br/>
error: Your local changes to the following files would be overwritten by merge:<br/>
        ProteomicsQCReportViewer/src/nl/ctmm/trait/proteomics/qcviewer/[some_package]/[SomeFile].java<br/>
Please, commit your changes or stash them before you can merge.<br/>
Aborting"

Stashing your local changes is very easy:<br/>
**`git stash`**<br/>
"Saved working directory and index state WIP on master: 123a456 [Some description.]<br/>
HEAD is now at 123a456 [Some description.]"


Maven
-----

We use [Maven](http://maven.apache.org/) as our build automation tool. This makes it easier to use certain tools (like Checkstyle and FindBugs) and to manage the third-party libraries (dependencies) we use. The pom.xml file for the viewer is stored in the ProteomicsQCReportViewer directory.

Some commonly used Maven commands are (see [Introduction to the Build Lifecycle](http://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html) for an explanation of the build lifecycle and the build phases):

\# Remove all files generated by the previous build:<br/>
**`mvn clean`**

\# Compile the source code of the project:<br/>
**`mvn compile`**

\# Test the compiled source code using a suitable unit testing framework:<br/>
**`mvn test`**

\# Take the compiled code and package it in its distributable format, such as a jar file:<br/>
**`mvn package`**

\# Install the package into the local repository, for use as a dependency in other projects locally:<br/>
**`mvn install`**

\# Perform a Checkstyle analysis, and generate a report on violations:<br/>
**`mvn checkstyle:checkstyle`**

\# Check if there are any FindBugs violations in the source code:<br/>
**`mvn findbugs:check`**


Checkstyle
----------

For the Java code of the viewer, we use Checkstyle to check for code style issues. Please check your code before committing. As we already mentioned in the Maven section, you can run **`mvn checkstyle:checkstyle`** to run Checkstyle on the code (you can run this command in the ProteomicsQCReportViewer directory, which contains the pom.xml file Maven needs). The report is generated in the target sub directory and is named checkstyle-result.xml.

It is also possible to configure a Java IDE (like Eclipse, IntelliJ or NetBeans) to integrate Checkstyle in your coding.


FindBugs
--------

FindBugs is a static code analysis tool like Checkstyle. If you run **`mvn findbugs:check`** in the ProteomicsQCReportViewer directory, FindBugs will generate the findbugsXml.xml file in the target directory.
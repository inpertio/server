## Table of Contents

* [1. Overview](#1-overview)
* [2. Availability](#2-availability)
* [3. Scalability](#3-scalability)
* [4. API](#4-api)
  * [4.1. YAML as key-value](#41-yaml-as-key-value)
    * [4.1.1 Hierarchy](#411-hierarchy)
    * [4.1.2. Value substitution](#412-value-substitution)
    * [4.1.3. Sensitive data](#413-sensitive-data)
  * [4.2. Raw resources](#42-raw-resources)
  * [4.3. Encryption](#43-encryption)
  * [4.4. Refresh](#44-refresh)
* [5. UI](#5-ui)
  * [5.1. Encrypt](#51-encrypt)
  * [5.2. Refresh](#52-refresh)
* [6. Configuration](#6-configuration)
  * [6.1. Git access](#61-git-access)
    * [6.1.1. SSH](#611-ssh)
    * [6.1.2. HTTPS](#612-https)
  * [6.2. Web](#62-web)
    * [6.2.1. Port](#621-port)
    * [6.2.2. Certificate](#622-certificate)
  * [6.3. Encryption](#63-encryption)
  * [6.4. Logger](#64-logger)
  * [6.5. Auto refresh](#65-auto-refresh)
* [7. Health check](#7-health-check)
* [8. Extension](#8-extension)
  * [8.1. Access control](#81-access-control)
* [9. Distribution](#9-distribution)

## 1. Overview

This service allows to expose Git repository content via HTTP(S) for further consumption by end applications.

![high-level](../web/img/high-level-view.png)
 
It can be used from any programming language, however a [number of built-in clients are also provided](../client).

## 2. Availability

As soon as the Config Service starts and successfully clones target Git repo, it guarantees that repo content is exposed for programmatic consumption all the time. Later on new commits might be added to the Git repo and connectivity between the repo and the Config Service might break, that way the latest updates are not exposed. However, the problems are reported and last known repo state is still available.

Feel free to check [5.2. Refresh](#52-refresh) for information on how the config service gets config repo changes 

## 3. Scalability

As the Config Service API is plain HTTP, no special horizontal scalability is provided out of the box. Number of instances might be deployed in production and facaded by a load balancer

## 4. API

### 4.1. YAML as key-value

The service allows exposing YAML files stored in Git as key-value pairs list. Generally, the API looks like below:

```
GET http(s)://<host>:<port>/api/keyValue/<version>/<branch>/<paths>
```

Example:

```
GET http://127.0.0.1:8080/api/keyValue/v1/master/my-team/common,my-team/app1/common,my-team/app1/PROD/common,my-team/app1/PROD/EMEA
```

* `<version>` - as the service evolves, the endpoint contract might change. Old clients can still work with it, because they use endpoints of previous versions, that way we provide backward compatibility between new service and old client
* `<branch>` - Git config repo branch to use
* `<paths>` - comma-separated config files paths within configs Git repo. Define target configs hierarchy, see below for detailed documentation

#### 4.1.1. Hierarchy

Normally we want to use hierarchical configs with ability to override any common property in more-specific configs. Consider the following config repo structure:

```
my-team
   |
   |__common
   |    |
   |    |__my-team-common.yml
   |
   |__app1
        |
        |__common
        |    |
        |    |__app1-common.yml
        |
        |__PROD
            |
            |__common
            |    |
            |    |__app1-PROD-common.yml
            |
            |__EMEA
                |
                |__app1-EMEA.yml
```

When there is a request for paths `my-team/common,my-team/app1/common,my-team/app1/PROD/common,my-team/app1/PROD/EMEA`, it does the following:

1. Finds all YAML files for the given paths - if a path points to a file it's picked as is; if a path points to a directory, all files under it are picked up recursively
2. Converts them to key-value pairs
3. Values from config files located to the right in the request paths overwrite values from config files located to the left

This way we have a possibility to overwrite any common value in, say, TEST/UAT environment

#### 4.1.2. Value substitution

Sometimes we want to use the same value in multiple places. Consider example below:

```
my-team
   |
   |__PROD
   |    |
   |    |__US
   |        |
   |        |__US-common.yml
   |
   |__app1
        |
        |__PROD
            |
            |__common
                 |
                 |__app1-PROD-common.yml
```

The files might have the following content:

* *US-common.yml*
  ```
  service1:
    host: some-host1
    port: some-port1
    login: ${service1.login}
    password: ${service1.password}
  service2:
    host: some-host2
    port: some-port2
    login: ${service2.login}
    password: ${service2.password}
  ```

* *app1-PROD-common.yml*
  ```
  app1:
    services:
      login: my-login
      password: my-password
  service1:
    login: ${app1.services.login}
    password: ${app1.services.password}
  service2:
    login: ${app1.services.login}
    password: ${app1.services.password}
  ```

Highlights:
* *US-common.yml* contains either the common data for all applications (like `service1.host`) or application-specific data (`service1.login`)
* application-specific *app1-PROD-common.yml* defines target login and password (`service1.login`, `service1.password`). An important moment here is that in this particular case the same value is reused in multiple cases - `app1.services.login` is used either as `service1.login` or `service2.login`. Config service automatically substitutes such nested definition, regardless whether they are located in the same config file or not

In order for this setup to work, config service should be requested like below:

```
GET http://127.0.0.1:8080/api/keyValue/v1/master/my-team/app1/PROD/common,my-team/PROD/US
```

#### 4.1.3. Sensitive data

We might want to store sensitive data like passwords in configs Git repo. Instead of keeping them as plain text, we use their encrypted representations (check [this documentation section](#43-encryption) on how to get them).

Config service automatically decrypts them in responses. It's possible to [further restrict decryption process](#81-access-control).

Generally, encrypted values should have `{cipher}` prefix, example:

```
service1:
  login: ${service1.login}
  password: '{cipher}FKSAJDFGYOS8F7GLHAKERGFHLSAJ'
```

### 4.2. Raw resources

Sometimes we might want to get content of a file stored in Git as is. That is a common case for old applications where we want to benefit from using config service but switch to it gradually. That way we can put existing configs into Git config repo as is and just plug them into existing application.

This endpoint has the following format:

```
GET http(s)://<host>:<port>/api/resource/<version>/<branch>/<path>
```

Example:

```
GET https://127.0.0.1:8080/api/resource/v1/master/config/app1/US/config.xml
```

Note that it automatically [decrypts encrypted values](#413-sensitive-data) in the exposed resources.

### 4.3. Encryption

The config service uses symmetric key to decrypt encrypted config values. Later on they can be consumed by end applications as described [here](#413-sensitive-data). Key configuration details are described in the [corresponding documentation section](#63-encryption). In order to prepare the encrypted values, we can call the endpoint below:

```
POST http(s)://<host>:<port>/api/encrypt
<value-to-encypt>
``` 

### 4.4. Refresh

As [depicted above](#1-overview), the Config Service exposes Git configs repo content. When new commits are available, we can force-refresh the service to pick them up. It exposes the endpoint below for that:

```
PUT http(s)://<host>:<port>/api/refresh
```

Also automatic refreshes can be configured, please check [6.5. Auto refresh](#65-auto-refresh) for more details on that

## 5. UI

The config service provides a couple of web UI pages which facilitate common actions.

### 5.1. Encrypt

There is a web page which provides minimalistic UI around [/encrypt](#43-encryption) endpoint, it's available on this address:

```
http(s)://<host>:<port>/ui/encrypt
```

### 5.2. Refresh

There is a web page which provides minimalistic UI around [/refresh](#44-refresh) endpoint, it's available on this address:

```
http(s)://<host>:<port>/ui/refresh
```

## 6. Configuration

TBD

### 6.1. Git access

TBD

#### 6.1.1. SSH

TBD

#### 6.1.2. HTTPS

TBD

### 6.2. Web

TBD

#### 6.2.1. Port

TBD

#### 6.2.2. Certificate

TBD

### 6.3. Encryption

TBD

### 6.4. Logger

TBD

### 6.5. Auto refresh

TBD

## 7. Health check

TBD

## 8. Extension

TBD

### 8.1. Access control

TBD

## 9. Distribution

TBD
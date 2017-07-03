defenv Selfie() :-
  spec('Load the principal keypair'),
  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/registry.pem').
//  principal($1).

//
// Identity set (apply to all participants)
//

defcon makeMyIdentitySet(?CN) :-
  spec("Construct the identity set for self"),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defcon makeMySubjectSet() :-
  spec("Construct the subject set for self"),
  {
    link($Self).
    label("Subject($Self)").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postSubjectSet() :- [makeMySubjectSet()].


// IaaS: Attests to containers

defcon addIPToContainer(?IP, ?ContainerId) :-
  spec('Announce an IP address of a container'),
  {
    usesIP($ContainerId, $IP).
    label("Containers/$ContainerId").
  }.

defpost postIPOfContainer(?IP, ?ContainerId) :- [addIPToContainer(?IP, ?ContainerId)].

defcon addAllowedImgToContainer(?ImgId, ?ContainerId) :-
  spec('Announce an allowed image for a container'),
  {
    allowsImage($ContainerId, $ImgId).
    label("Containers/$ContainerId").
  }.

defpost postAllowedImgOfContainer(?ImgId, ?ContainerId) :- 
  [addAllowedImgToContainer(?ImgId, ?ContainerId)].

defcon addAllowedEndpointToContainer(?Endpoint, ?ContainerId) :-
  spec('Announce an allowed endpoint of a container'),
  {
    allowsEndpoint($ContainerId, $Endpoint).
    label("Containers/$ContainerId").
  }.

defpost postAllowedEndpointOfContainer(?Endpoint, ?ContainerId) :- 
  [addAllowedEndpointToContainer(?Endpoint, ?ContainerId)].

defcon addRBucketToContainer(?RBucket, ?ContainerId) :-
  spec('Announce a read-only bucket of a container'),
  {
    readBucket($ContainerId, $RBucket).
    label("Containers/$ContainerId").
  }.

defpost postRBucketOfContainer(?RBucket, ?ContainerId) :-
  [addRBucketToContainer(?RBucket, ?ContainerId)].

defcon addRWBucketToContainer(?RWBucket, ?ContainerId) :-
  spec('Announce a read-write bucket of a container'),
  {
    readBucket($ContainerId, $RWBucket).
    writeBucket($ContainerId, $RWBucket).
    label("Containers/$ContainerId").
  }.

defpost postRWBucketOfContainer(?RWBucket, ?ContainerId) :- 
  [addRWBucketToContainer(?RWBucket, ?ContainerId)].

defcon addAllowedAPIToContainer(?API, ?ContainerId) :-
  spec('Announce an allowed API of a container'),
  {
    allowsAPI($ContainerId, $API).
    label("Containers/$ContainerId").
  }.

defpost postAllowedAPIOfContainer(?API, ?ContainerId) :- 
  [addAllowedAPIToContainer(?API, ?ContainerId)].


// Policies and guards

defguard getContainerSetToken(?ContainerId) :-
  spec("Query the token of a container set"),
  ?Token := label("Containers/?ContainerId"),
  {
    containerSetToken($Token).
    containerSetToken(?X)?
  }.

defguard getSetToken(?Type, ?Id) :-
  spec("Query the token of a set"),
  ?Token := label("?Type/?Id"),
  {
    setToken($Token).
    setToken(?X)?
  }.

defcon imagePolicySet() :-
  spec('Policies for images'),
  { 
    validImage(?ContainerId, ?ImgId) :- allowsImage(?ContainerId, ?ImgId).
    label("Policies/image").
  }.

definit imagePolicySet().

defguard checkValidImage(?ContainerId, ?ImgId) :-
  spec("Check if an image is allowed in a container"),
  ?ImagePolicySet := label("Policies/image"),
  {
    link($BearerRef).
    link($ImagePolicySet).
    validImage($ContainerId, $ImgId)?
  }.

defcon networkPolicySet() :-
  spec('Policies for networking'),
  { 
    reachable(?InstanceId, ?Endpoint) :- hasInstance(?ContainerId, ?InstanceId), allowsEndpoint(?ContainerId, ?Endpoint).
    label("Policies/network").
  }.

definit networkPolicySet().

defguard checkReachability(?InstanceId, ?Endpoint) :-
  spec("Check if an instance is allowed to talk to an endpoint"),
  ?NetworkPolicySet := label("Policies/network"),
  {
    link($BearerRef).
    link($NetworkPolicySet).
    reachable($InstanceId, $Endpoint)?
  }.


// IaaS: Attests to instances

defcon createInstance(?InstanceId, ?ContainerId, ?ImageId) :-
  spec('Initialize an instance set'),
  ?ContainerSet := label("Containers/?ContainerId"),
  ?ImageSet := label("Images/?ImageId"),
  {
    link($ContainerSet).
    link($ImageSet).
    label("Instances/$InstanceId").
  }.

defpost createInstanceSet(?InstanceId, ?ContainerId, ?ImageId) :-
  [createInstance(?InstanceId, ?ContainerId, ?ImageId)].

defcon addInstance(?InstanceId, ?ContainerId) :-
  spec('Announce an instance of a container'),
  {
    hasInstance($ContainerId, $InstanceId).
    label("Containers/$ContainerId").
  }.

defpost postInstanceOfContainer(?InstanceId, ?ContainerId) :-
  [addInstance(?InstanceId, ?ContainerId)].

defcon addInstanceProperties(?InstanceId, ?ImgId, ?InternalIP, ?InternalDetachable, ?PublicIP, ?PublicDetachable, ?IPExpirationTime) :-
  spec('Issue important properties of an instance'),
  {
    instanceImage($InstanceId, $ImgId).
    instanceInternalIP($InstanceId, $InternalIP, $InternalDetachable).
    instancePublicIP($InstanceId, $PublicIP, $PublicDetachable, $IPExpirationTime).
    label("Instances/$InstanceId").
  }.

defpost postInstanceProperties(?InstanceId, ?ImgId, ?InternalIP, ?InternalDetachable, ?PublicIP, ?PublicDetachable, ?IPExpirationTime) :- 
  [addInstanceProperties(?InstanceId, ?ImgId, ?InternalIP, ?InternalDetachable, ?PublicIP, ?PublicDetachable, ?IPExpirationTime)].

defcon addAppInstallation(?AppId, ?AppInfo, ?InstanceId) :-
  spec('Install an app on an instance'),
  {
    runApp($InstanceId, $AppId, $AppInfo).
    label("Instances/$InstanceId").
  }.

defpost postInstalledAppToInstance(?AppId, ?AppInfo, ?InstanceId) :-
  [addAppInstallation(?AppId, ?AppInfo, ?InstanceId)].



defcon addImageProperties(?ImgId, ?KernelVersion, ?BaseImgVersion, ?InstallScriptHash, ?OmittedFilesHash) :-
  spec('Issue important properties of an instance'),
  {
    imageProperties($ImgId, $KernelVersion, $BaseImgVersion, $InstallScriptHash, $OmittedFilesHash).
    label("Images/$ImgId").
  }.

defpost postImageProperties(?ImgId, ?KernelVersion, ?BaseImgVersion, ?InstallScriptHash, ?OmittedFilesHash) :-
  [addImageProperties(?ImgId, ?KernelVersion, ?BaseImgVersion, ?InstallScriptHash, ?OmittedFilesHash)].



// Assumes that the caller already posted the instance's property set

defcon addVolumeToInstance(?VolumeId, ?InstanceId) :-
  spec('Attach a volume to an instance'),
  {
    attachedVolume($InstanceId, $VolumeId).
    label("Instances/$InstanceId").
  }.


// Instance: attests to Dockers

defcon addDockerInstance(?DockerId, ?InstanceId) :-
  spec('Announce a Docker instance inside a VM instance'),
  ?InstanceSet := label("Instances/?InstanceId"),
  {
    link($InstanceSet).
    launchesDocker($InstanceId, $DockerId).
    label("Dockers/$DockerId").
  }.


// Docker: attests to apps

defcon addApp(?AppId, ?DockerId) :-
  spec('Announce an app launched from docker'),
  ?DockerSet := label("Dockers/?DockerId"),
  {
    link($DockerSet).
    launchesApp($DockerId, $AppId).
    label("Apps/$AppId").
  }.

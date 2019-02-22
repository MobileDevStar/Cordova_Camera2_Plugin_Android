// JavaScript Document

/**
 * Provides local event queuing
 *
 * @class FileApi
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
var FileApi = (function(){
	
	var aFileErrors = ['NO_ERROR','NOT_FOUND_ERR','SECURITY_ERR','ABORT_ERR','NOT_READABLE_ERR','ENCODING_ERR','NO_MODIFICATION_ALLOWED_ERR','INVALID_STATE_ERR','SYNTAX_ERR','INVALID_MODIFICATION_ERR','QUOTA_EXCEEDED_ERR','TYPE_MISMATCH_ERR','PATH_EXISTS_ERR'];
	
	var aTransferErrors = ['NO_ERROR','FILE_NOT_FOUND_ERR','INVALID_URL_ERR','CONNECTION_ERR','ABORT_ERR','NOT_MODIFIED_ERR'];
	
	var rPhotoOnly = /^(?!.*(?:_thumb))/;

	/**
	* Create a nested folder from a string in one of the standard filesystem locations
	* 
	* @public
	* @memberOf FileApi#
	* @param {integer} iRoot - The filesystem. 0 = PERSISTENT, 1 = TEMPORARY
	* @param {string} sPath - The relative folder path (a/b/c)
	* @param {function} fnSuccess - Success callback (object)
	* @param {function} fnFail - Error callback
	*/
	function createDirectory(iRoot, sPath, fnSuccess, fnFail){
		//console.log(LocalFileSystem.PERSISTENT);
		
		// Strip leading ad trailing slashes
		sPath = stripOuterSlashes(sPath);
	
		var aPath = sPath.split('/');
		
		//console.log(aPath);
		
		if (typeof(iRoot) == 'undefined'){
			iRoot = LocalFileSystem.PERSISTENT;
		}
		
		var fsAttach = function(){
			// Get file system to copy or move image file to
			requestFileSystem(iRoot, 0, function(fileSystem) {
				console.log('FileApi: requestFileSystem success ' + fileSystem.name);
				
				//alert('Default Image Directory ' + fileEntry.fullPath);
				
				fsCreate(fileSystem.root, aPath);
				
			},function(error){
				console.error('FileApi: requestFileSystem failed ' + error.code);
				msg_error('Error attaching filesystem');
				
				if (typeof(fnFail) == 'function'){
					fnFail();
				}
			});
		};
		
		var fsCreate = function(directoryEntry,aPathTemp){
			console.log(aPathTemp);
			var sFolder = aPathTemp.shift();
			
			directoryEntry.getDirectory(sFolder, {
				create : true
			}, function(directoryEntry) {
				console.log('FileApi: getDirectory success ' + directoryEntry.fullPath);
				//console.log(directoryEntry);
				
				if (aPathTemp.length > 0){
					fsCreate(directoryEntry, aPathTemp);
				} else {
					if (typeof(fnSuccess) == 'function'){
						fnSuccess(directoryEntry);
					}
				}
			}, function(error){
				console.error('FileApi: getDirectory failed ' + error.code);
				//msg_error('Error creating folder');
				if (typeof(fnFail) == 'function'){
					fnFail();
				}
			});
		};
		
		fsAttach();
	}
	
	
	/**
	* Create a nested folder from a string in one of the standard filesystem locations
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sPath - The absolute folder path (a/b/c)
	* @param {function} fnSuccess - Success callback (object)
	* @param {function} fnFail - Error callback
	*/
	function createDirectoryAny(sPath, fnSuccess, fnFail){
		
		console.log('FileApi: createDirectoryAny sPath: ' + sPath);
	
		//var aPath = sPath.split('/'); // Doesn't handle protocol correctly
		var oPath = splitPath(sPath);
		//console.log(oPath);
		
		var sRoot = oPath['sRoot']; // ie. file:///
		var aPath = oPath['aPath'];

		var fnCreate = function(directoryEntry,aPathTemp){
			//console.log(aPathTemp);
			var sFolder = aPathTemp.shift(); // Affects aPath
			
			directoryEntry.getDirectory(sFolder, {
				create : true
			}, function(directoryEntry) {
				console.log('FileApi: getDirectory success ' + directoryEntry.fullPath);
				//console.log(directoryEntry);
				
				if (aPathTemp.length > 0){
					fnCreate(directoryEntry, aPathTemp);
				} else {
					if (typeof(fnSuccess) == 'function'){
						fnSuccess(directoryEntry);
					}
				}
			}, function(error){
				console.error('FileApi: getDirectory failed ' + error.code);
				//msg_error('Error creating folder');
				if (typeof(fnFail) == 'function'){
					fnFail();
				}
			});
		};
		
		//fsCreate(fileSystem.root, aPath);
		
		// Attempt to get a root directory entry
		resolve(sRoot,
		function(oEntry){
			console.log('FileApi: createDirectoryAny path already exists: ' + sPath);
			
			// This was a mistake...
			//if (typeof(fnSuccess) == 'function'){
			//	fnSuccess(oEntry);
			//}
			
			fnCreate(oEntry, aPath);
		},
		function(){
			console.error('FileApi: createDirectoryAny resolve path not found: ' + sRoot);
			
			//fsCreate(oEntry, aPath); // Shouldn't be here
		});
	}

	/**
	* Remove a folder
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sPath - The absolute folder path
	* @param {function} fnSuccess - Success callback (object)
	* @param {function} fnFail - Error callback (object)
	*/	
	function removeDirectory(sPath,fnSuccess,fnError){
		console.log('FileApi: removeDirectory');
		console.log(sPath);
		
		var fnRequestSuccess = function(oDir){
			console.log('FileApi: removeDirectory > fnRequestSuccess - path: ' + oDir.fullPath);
			oDir.removeRecursively(fnSuccess, fnError);
		};
		
		var fnRequestError = function(oError){
			console.error('FileApi: removeDirectory > fnRequestError');
			
			// Folder not found
			if (oError['code'] == 1){
				fnSuccess();
			} else {
				console.error(oError);		
				fnError(oError);
			}
		};
		
		resolve(sPath, fnRequestSuccess, fnRequestError);
	}
	
	/**
	* Get the absolute path to persistent storage
	* 
	* @public
	* @memberOf FileApi#
	* @param {function} fnSuccess - Success callback (object)
	*/	
	function getPersistentPath(fnComplete){
		//if (oConstants['bIsMobile']){
			window.requestFileSystem(LocalFileSystem.PERSISTENT, 0, function(fileSystem){
				console.log("FileApi: fileSystem.root.toURL(): "+fileSystem.root.toURL()); // ANDROID: file:///data/data/com.propertypreswizard.app.propertypreswizard/files/files/
				console.log("FileApi: fileSystem.root.toInternalURL(): "+fileSystem.root.toInternalURL()); // cdvfile://localhost/persistent/
				console.log("FileApi: fileSystem.root.nativeURL: "+fileSystem.root.nativeURL); // ANDROID: file:///data/data/com.propertypreswizard.app.propertypreswizard/files/files/
				
				if (typeof(fnComplete) == 'function'){
					fnComplete(fileSystem.root.toURL());
				}
			});
		//}
	}
	
	/**
	* Translate error code
	* 
	* @public
	* @memberOf FileApi#
	* @param {integer} iCode - The error code
	* @returns {string} The error message
	*/
	function getError(iCode){
		return aFileErrors[iCode];
	}
	
	/**
	* Recursive file list - returns nested object
	* 
	* @public
	* @memberOf FileApi#
	* @param {object} oOptions - Accepts multiple options
	* @param {object} oOptions.rFilter - A compiled regular expression to filter on
	* @param {boolean} oOptions.bRecurse - Recurse into subfolders?
	* @param {boolean} oOptions.bIncludeFolders - Include subfolders? Applies to flat mode only.
	* @param {integer} oOptions.iFilePathType - 0: Filename only, 1: Relative path, 2: Full URL
	* @param {boolean} oOptions.bFlatten - Flatten output into an array?
	* @param {function} oOptions.fnSuccess - Success callback. (array/object)
	* @param {function} oOptions.fnError - Error callback. (object)
	*/	
	function list(oOptions){
		console.log('FileApi: list');
		
		// Defaults
		oOptions['rFilter'] = (typeof(oOptions['rFilter']) != 'undefined') ? oOptions['rFilter'] : ''; // Compiled regex
		oOptions['bRecurse'] = (typeof(oOptions['bRecurse']) != 'undefined') ? oOptions['bRecurse'] : false; // Recurse?
		oOptions['bIncludeFolders'] = (typeof(oOptions['bIncludeFolders']) != 'undefined') ? oOptions['bIncludeFolders'] : true; // Include folders in flattened outpur
		oOptions['iFilePathType'] = (typeof(oOptions['iFilePathType']) != 'undefined') ? oOptions['iFilePathType'] : 1; // 0: Filename only, 1: Relative path, 2: Full URL
		oOptions['bFlatten'] = (typeof(oOptions['bFlatten']) != 'undefined') ? oOptions['bFlatten'] : false; // Flatten into array
		oOptions['fnSuccess'] = (typeof(oOptions['fnSuccess']) == 'function')? oOptions['fnSuccess'] : function(){}; // Success callback
		oOptions['fnError'] = (typeof(oOptions['fnError']) == 'function')? oOptions['fnError'] : function(){}; // Error callback
		oOptions['iDepth'] = oOptions['iDepth'] || 0; // Recursion depth
		
		//console.log('FileApi: list Path: ' + oOptions['sPath'] + ' Depth: ' + oOptions['iDepth']);
	
		//console.log('sPath: ' + oOptions['sPath'] + ' iDepth: ' + oOptions['iDepth']);
		
		// Queue directory recursion
		var fnQueue = function(oEntry,oOptions2){
			return function(fnCallback){
				if(oOptions2['bRecurse']){
					//console.log(oEntry);
								
					//var oOptions2 = $.extend( {}, oOptions);
					
					oOptions2['sPath'] = oEntry.nativeURL; // 1- photos 2- 2376888
					oOptions2['fnSuccess'] = function(mFolder){
						//console.log(mFolder); // If flatten is true, this should be an array
						//aoFolders.push(mFolder);
						
						//console.log('oEntry',oEntry);
						//console.log("oOptions['bIncludeFolders']: " + oOptions['bIncludeFolders']);
						
						if (oOptions['bFlatten']){
							//var aMerged = [];
							
							// Insert the folder name if option is set
							
							
							
							if (oOptions['bIncludeFolders']){
								var sTempName = '';
								switch(oOptions['sFilePathType']){
									case 0: 
										sTempName = oEntry['name'];
										break;
									case 1: 
										sTempName = oEntry['fullPath'];
										break;
									case 2:
										sTempName = oEntry['nativeURL'];
										break;
									default:
										sTempName = oEntry['name'];
								}
								mFolder.push(sTempName);							
							}
							
							//aMerged = aMerged.concat(aFiles); // Merge files found on current node

						} else {
							mFolder['name'] = oEntry['name']; // Inject folder name
						}
							
						
						//var aTemp = [mFolder,oEntry];
						
						fnCallback(null, mFolder); // Appends to output array
					}
					oOptions2['iDepth'] = oOptions2['iDepth']+1;
					
					//console.log('sPath: ' + oOptions2['sPath'] + ' iDepth: ' + oOptions2['iDepth']);
					
					list(oOptions2); // Recurse
				} else {
					fnCallback(null, oEntry['name']);
				}
			};
		};
		
		var sCurrent = '';
	
		// Process folder
		resolve(oOptions['sPath'], 
		function(oDirectoryEntry){
			//console.log(oDirectoryEntry);
			sCurrent = oDirectoryEntry['name']; // Copy to parent context
			
			//console.log(sCurrent); // ex: photos
			
			// Create relative path from source
			var sRelativePath = '';
			
			if (typeof(oOptions['sRelativePath']) == 'undefined'){
				sRelativePath = '/';
			} else {
				sRelativePath = oOptions['sRelativePath'] + sCurrent + '/';
			}
			
			//console.log('sRelativePath: ' + sRelativePath);
			
			var oDirectoryReader = oDirectoryEntry.createReader();
			oDirectoryReader.readEntries(function(aoEntries) {
				//console.log('FileApi: directoryReader.readEntries');
				//console.log(aoEntries);
							
				var aFiles = [];
				//var aoFolders = []; // Not conducive to async pattern
				var aQueue = [];
				
				for (var i=0; i<aoEntries.length; i++) {
					var oEntry = aoEntries[i];
					
					//console.log(oEntry);
					
					// Files ONLY
					if (oEntry.isFile == true){
						
						//console.log('isFile oEntry',oEntry);
						
						// If there is no filter or the filter passes
						if (oOptions['rFilter'] == '' || oOptions['rFilter'].test(oEntry['name'])){
						
							// Return plain text filename for flatten option, else full object
							if(oOptions['bFlatten']){
								var sTempName = '';
								switch(oOptions['iFilePathType']){
									case 0: 
										sTempName = oEntry['name']; 
										// ex: 681485_1469219698488.jpg
										break;
									case 1: 
										sTempName = oEntry['fullPath']; 
										// ex: /photos/681485/681485_1469219698488.jpg
										break;
									case 2:
										sTempName = oEntry['nativeURL']; 
										// ex: file:///data/data/com.propertypreswizard.app.propertypreswizard/files/files/photos/681485/681485_1469219698488.jpg
										break;
									case 3:
										sTempName = sRelativePath + oEntry['name'];
										// ex: /681485/681485_1469219698488.jpg
										break;
									default:
										sTempName = oEntry['name'];
								}

								aFiles.push(sTempName);

							} else {
								aFiles.push(oEntry);
							}
						}
					} else if(oEntry.isDirectory == true){
						
						//console.log('isDirectory oEntry',oEntry);
						
						var oOptions2 = $.extend( {}, oOptions);
						
						oOptions2['sRelativePath'] = sRelativePath;		
						
						//var sDirectoryName = oEntry['name'];
						//console.log("sDirectoryName1: " + sDirectoryName);
						
						if (oOptions['bIncludeFolders']){
							// Build queue
							aQueue.push(fnQueue(oEntry,oOptions2));							
						}

					}
				}
				
				//if(aQueue.length > 0){
				//console.log('aQueue.length ' + aQueue.length);
				
					// Playback queue
					async.series(aQueue,function(oError,oResults){ 
						//console.log('async.series callback');
						//console.log(oResults); // Mixed array or object
						
						//var oTree = oResults[0];
						//var oDirectoryEntry = oResults[1];
						
						
						if(oOptions['bFlatten']){
							//console.log(aFiles)
							//console.log(aoFolders);
							
							var aMerged = [];
/*							
							// Insert the folder name if option is set
							if (oOptions['bIncludeFolders']){
								var sTempName = '';
								switch(oOptions['sFilePathType']){
									case 0: 
										sTempName = oDirectoryEntry['name'];
										break;
									case 1: 
										sTempName = oDirectoryEntry['fullPath'];
										break;
									case 2:
										sTempName = oDirectoryEntry['nativeURL'];
										break;
									default:
										sTempName = oDirectoryEntry['name'];
								}
								aMerged.push(sTempName);							
							}
*/							
							aMerged = aMerged.concat(aFiles); // Merge files found on current node
							
							// Loop through oResults
							for(var x=0;x<oResults.length;x++){
								aMerged = aMerged.concat(oResults[x]); // Merge files found deeper
							}
							//console.log(aMerged);
							
							oOptions['fnSuccess'](aMerged); // Fire callback
						} else {
							
							// Loop through oResults
							//for(var x=0;x<oResults.length;x++){
							//	oResults[x][0]['name'] = oResults[x][1]['name'];
							//	aMerged.push(oResults[x][0]); // Merge files found deeper
							//}

							//console.log("sDirectoryName2: " + sDirectoryName);
							var oNode = {
								//name: oDirectoryEntry['name'], // The folder we are in, not the folder we are returning
								//name: sCurrent,
								files: aFiles,
								folders: oResults
							};
							
/*							console.log('sPath: ' + oOptions['sPath'] + ' iDepth: ' + oOptions['iDepth']);
							console.log(oResults)
							console.log('sCurrent: ' + sCurrent);
							console.log("oDirectoryEntry['name']: " + oDirectoryEntry['name']);
							console.log(oDirectoryEntry);*/
							
							oOptions['fnSuccess'](oNode); // Fire callback
						}
					});
				//} else {
				//}
	
			}, function (oError) {
				console.error('FileApi: list error directoryReader.readEntries code: ' + oError.code);
				console.log(oDirectoryEntry);
				
				if(oOptions['bFlatten']){
					oOptions['fnSuccess']([]);
				} else {
					oOptions['fnSuccess']({});
				}
			});	
		}, 
		function (oError) {
			console.error('FileApi: list resolveLocalFileSystemURL error Path: ' + oOptions['sPath'] + ' Error: ' + FileApi.getError(oError.code));
			oOptions['fnError']();
		});
	}
		
	/**
	* Recursively walk through a folder, firing a callback for each file
	* 
	* @public
	* @memberOf FileApi#
	* @param {object} oOptions - Accepts multiple options
	* @param {object} oOptions.rFilter - A compiled regular expression to filter on
	* @param {boolean} oOptions.bRecurse - Recurse into subfolders?
	* @param {integer} oOptions.iFilePathType - 0: Filename only, 1: Relative path, 2: Full URL
	* @param {function} oOptions.fnEach - Called against each file (function)
	* @param {function} oOptions.fnDone - Done callback
	*/	
	function walkFS(oOptions){

		// Defaults
		oOptions['rFilter'] = (typeof(oOptions['rFilter']) != 'undefined') ? oOptions['rFilter'] : ''; // Compiled regex
		oOptions['bRecurse'] = (typeof(oOptions['bRecurse']) != 'undefined') ? oOptions['bRecurse'] : false; // Recurse?
		oOptions['iFilePathType'] = (typeof(oOptions['iFilePathType']) != 'undefined') ? oOptions['iFilePathType'] : 1; // 0: Filename only, 1: Relative path, 2: Full URL
		//oOptions['sRelativePath'] = (typeof(oOptions['sRelativePath']) != 'undefined') ? oOptions['sRelativePath'] : '/'; // 0: Filename only, 1: Relative path, 2: Full URL
		
		oOptions['fnDone'] = (typeof(oOptions['fnDone']) == 'function')? oOptions['fnDone'] : function(){}; // Success callback
		
		//oOptions['fnSuccess'] = (typeof(oOptions['fnSuccess']) == 'function')? oOptions['fnSuccess'] : function(){}; // Success callback
		//oOptions['fnError'] = (typeof(oOptions['fnError']) == 'function')? oOptions['fnError'] : function(){}; // Error callback
		
		oOptions['fnEach'] = (typeof(oOptions['fnEach']) == 'function')? oOptions['fnEach'] : function(oFile,sRelativePath,fnNext){fnNext()}; // Error callback
		oOptions['iDepth'] = oOptions['iDepth'] || 0; // Recursion depth
		
		console.log('FileApi: walkFS Path: ' + oOptions['sPath'] + ' Depth: ' + oOptions['iDepth']);		
		
		// Queue a file
		var fnQueueFile = function(oFile,oOptions2){
			return function(fnNext){
				// Copy file
				//console.log('copying file: ' + oFile['name']);
				console.log(oFile);
				
				var sRelativePath = oOptions2['sRelativePath'];
				
				oOptions2['fnEach'](oFile,sRelativePath,fnNext);
			};
		};
		
		// Queue a folder
		var fnQueueFolder = function(oFolder, oOptions2){
			return function(fnNext){

				oOptions2['sPath'] = oFolder.nativeURL; // 1- photos 2- 2376888
				//oOptions2['sRelativePath'] = oOptions2['sRelativePath'] + oFolder['name'] + '/';
				
				console.log("oOptions2['sRelativePath']: " + oOptions2['sRelativePath'] + ' oFolder.name: ' + oFolder.name);
				
				oOptions2['fnDone'] = function(mFolder){
					//console.log(oFolder);
					
					// FOLDER CALLBACK HERE <-----------------------------------------------------------

					//mFolder['name'] = oEntry['name']; // Inject folder name
					
					fnNext(null); // Appends to output array
				}
				oOptions2['iDepth'] = oOptions2['iDepth']+1;
				
				walkFS(oOptions2); // Recurse					
			};
		};
		
		var sCurrent = '';
	
		// Process folder
		resolve(oOptions['sPath'], 
		function(oDirectoryEntry){
			console.log(oDirectoryEntry);

			sCurrent = oDirectoryEntry['name']; // Copy to parent context
			
			// Create relative path from source
			//var sRelativePath = '';
			
			if (typeof(oOptions['sRelativePath']) == 'undefined'){
				oOptions['sRelativePath'] = '/';
			} else {
				oOptions['sRelativePath'] = oOptions['sRelativePath'] + sCurrent + '/';
			}
			
			console.log("oOptions['sRelativePath']: " + oOptions['sRelativePath']);
			
			//oOptions['sRelativePath'] = oOptions['sRelativePath'] + sCurrent + '/';
			
			var oDirectoryReader = oDirectoryEntry.createReader();
			oDirectoryReader.readEntries(function(aoEntries) {
					
				var aFileQueue = [];		
				var aFolderQueue = [];
				
				// Loop through all files and folders
				for (var i=0; i<aoEntries.length; i++) {
					var oEntry = aoEntries[i];
					var oOptions2 = $.extend( {}, oOptions);
					
					//oOptions2['sRelativePath'] = sRelativePath;					
					
					// Files ONLY
					if (oEntry.isFile == true){
						
						// If there is no filter or the filter passes
						if (oOptions['rFilter'] == '' || oOptions['rFilter'].test(oEntry['name'])){							
							aFileQueue.push(fnQueueFile(oEntry,oOptions2));
						}
					} else if(oEntry.isDirectory == true){
		
						if(oOptions2['bRecurse']){
							aFolderQueue.push(fnQueueFolder(oEntry,oOptions2));
						}
					}
				} // For loop
				
				console.log('File count: ' + aFileQueue.length + ' Folder count: ' + aFolderQueue.length);
				
				// Process files in serial before processing folders
				async.series(aFileQueue,function(oError,oResults){ 
					
					// Process folders serially
					async.series(aFolderQueue,function(oError,oResults){ 
	
						oOptions['fnDone'](); // Fire callback
					});
				});
				


			}, function (oError) {
				console.error('FileApi: list error directoryReader.readEntries code: ' + oError.code);
				console.log(oDirectoryEntry);

				oOptions['fnDone']();
			});	
		}, 
		function (oError) {
			console.error('FileApi: list resolveLocalFileSystemURL error Path: ' + oOptions['sPath'] + ' Error: ' + FileApi.getError(oError.code));
		});
	}
	
	/**
	* Recurse through a nested array of files? #UNUSED
	* 
	* @public
	* @memberOf FileApi#
	* @param {object} oObj - A nested object consisting of file and folder arrays
	* @param {function} fnCallback - Success callback (object)
	*/	
	function nestFiles(oObj, fnCallback){
		
		var iCount = 0;
		
		var recurseAsync = function(oObj, iDepth){
			iDepth = (typeof(iDepth) == 'undefined') ? 0 : iDepth;
			
			console.log('Name: ' + oObj['name'])
			
			var proxy = function(){
				if (typeof(oObj['folders']) != 'undefined' && oObj['folders'].length > 0){
					aFolders = oObj['folders'];
					
					for (var x=0;x<oObj['folders'].length;x++){
						aFolder = aFolders[x];
						
						recurseAsync(aFolder, iDepth+1)
					}
	
				}
				
				iCount--;
				
				console.log('iCount: ' + iCount);
				
				if (iCount == 0){
					fnCallback();
				}
			};
			
			iCount++;
			
			setTimeout(proxy,250);
		};
		
		recurseAsync(oObj, fnCallback);
	}
	
	/**
	* Delete a single file
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sPath - An absolute file path
	* @param {function} fnSuccess - Success callback
	* @param {function} fnError - Error callback (object)
	* @param {function} fnResolveError - Resolve error callback (object)
	*/	
	function deleteFile(sPath,fnSuccess,fnError,fnResolveError){
		console.log('FileApi: deleteFile path: ' + sPath);
		
/*		if (typeof(fnSuccess) != 'function'){
			console.log('FileApi: deleteFile > fnSuccess not a function');
		}
		
		if (typeof(fnError) != 'function'){
			console.log('FileApi: deleteFile > fnError not a function');
		}
		
		if (typeof(fnResolveError) != 'function'){
			console.log('FileApi: deleteFile > fnResolveError not a function');
		}*/
		
		if (typeof(fnResolveError) == 'undefined'){
			var fnResolveError = function(){
				console.error('FileApi: Unable to resolve file ' + sPath);
			};
		}
		
		// Attach to file
		resolveLocalFileSystemURL(sPath, function(fileEntry){
			console.log(fileEntry);
			fileEntry.remove(fnSuccess,fnError);
		},fnResolveError);
	}
	
	/**
	* Copies a folder or file. Throws error if file or folder already exists, including in subfolders
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sSource - An absolute path to a file/folder
	* @param {string} sDestination - An absolute path to a folder
	* @param {string} sName - Alter the name of the copied file/folder
	* @param {function} fnSuccess - Success callback
	* @param {function} fnError - Error callback (object)
	* @param {function} fnResolveError - Resolve error callback (object)
	*/	
	function copy(sSource,sDestination,sName,fnSuccess,fnError,fnResolveError){
		console.log('FileApi: copy');
		resolve(sSource,function(oSourceEntry){
			console.log(oSourceEntry);
			
			resolve(sDestination,function(oDestinationEntry){	
				console.log(oDestinationEntry);
				
				oSourceEntry.copyTo(oDestinationEntry, sName, fnSuccess,fnError);
			},fnResolveError);
		},fnResolveError);
	}

	/**
	* Copies a file. Target folder will be created if it doesn't exist
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sSource - An absolute path to a file/folder
	* @param {string} sTarget - An absolute path to a folder
	* @param {string} sName - Alter the name of the copied file/folder
	* @param {function} fnSuccess - Success callback
	* @param {function} fnError - Error callback (object)
	*/		
	function copyFile(sSource,sTarget,sName,fnSuccess,fnError){
		console.log('FileApi: copyFile');
		console.log('sSource: ' + sSource);
		console.log('sTarget: ' + sTarget);
		
		// Set default for filename
		if (typeof(sName) == 'undefined'){
			sName = null;
		}
		
		var oSourceCopy;
		
		var fnCopy = function(oTarget){
			oSourceCopy.copyTo(oTarget, sName, fnSuccess,fnError);
		};
		
		// Resolve the source folder
		resolve(sSource,function(oSource){
			console.log(oSource);
			
			oSourceCopy = oSource;
			
			// ATTEMPT to resolve the target
			resolve(sTarget,function(oTarget){	
				console.log(oTarget);
				
				fnCopy(oTarget); // Copy the file
			},function(){
				// If the path can't be resolved, attempt to create it
				createDirectoryAny(sTarget, 
				function(oTarget){
					fnCopy(oTarget); // Copy the file
				}, 
				fnError);
			});
		},fnError);
	}
	
	/**
	* Copies a file. Target folder will be created if it doesn't exist
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sSource - An absolute path to a file/folder
	* @param {string} sTarget - An absolute path to a folder
	* @param {string} sName - Alter the name of the copied file/folder
	* @param {function} fnSuccess - Success callback
	* @param {function} fnError - Error callback (object)
	*/		
	function moveFile(sSource,sTarget,sName,fnSuccess,fnError){
		console.log('FileApi: moveFile - sSource: ' + sSource + ', sTarget: ' + sTarget);

		// Set default for filename
		if (typeof(sName) == 'undefined'){
			sName = null;
		}
		
		var oSourceCopy;
		
		var fnCopy = function(oTarget){
			oSourceCopy.moveTo(oTarget, sName, fnSuccess,fnError);
		};
		
		// Resolve the source folder
		resolve(sSource,function(oSource){
			//console.log('FileApi: moveFile - source File',oSource);
			
			oSourceCopy = oSource;
			
			// ATTEMPT to resolve the target
			resolve(sTarget,function(oTarget){	
				//console.log('FileApi: moveFile - Target exists',oTarget);
				
				fnCopy(oTarget); // Copy the file
			},function(){
				// If the path can't be resolved, attempt to create it
				createDirectoryAny(sTarget,
				function(oTarget){
					//console.log('FileApi: moveFile - Target created',oTarget);
					fnCopy(oTarget); // Copy the file
				}, 
				fnError);
			});
		},fnError);
	}

	/**
	* Copies a folder. Target folder will be created if it doesn't exist
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sSource - An absolute path to a file/folder
	* @param {string} sTarget - An absolute path to a folder
	* @param {function} fnSuccess - Success callback
	* @param {function} fnError - Error callback (object)
	*/		
	function copyFolder(sSourceFolder,sTargetFolder,fnSuccess,fnError){
		
		var aErrors = [];
		//console.log(aErrors);
		
		var fnListSuccess = function(aoFiles){
			console.log(aoFiles);
			//hideLoading()
			
			FileApi.walk(aoFiles,function(oFile,sPath,fnNext){
				console.log(oFile);
				console.log('sPath: ' + sPath + ': ' + oFile.name);
				
				sPath = stripOuterSlashes(sPath);
				
				var sSource = oFile['nativeURL'];
				var sTarget = sTargetFolder + sPath;
				
				copyFile(sSource,sTarget,null,function(){
					fnNext();
				},function(oError){
					//aErrors.push(oError);
					aErrors.push({sSource: sSource,sTarget: sTarget});
					fnNext();
				});
				
				
			},function(){
				console.log('Recursive file copy complete');
				
				if (aErrors.length > 0){
					if (typeof(fnError) != 'undefined'){
						fnError();
					}							
				} else {
					if (typeof(fnSuccess) != 'undefined'){
						fnSuccess();
					}					
				}
			});
			
			// NEEDS ERROR HANDLING 12/30/16
		};
		
		var fnListError = function(){
		};
		
		
		var oOptions = {
			sPath: sSourceFolder,
			//rFilter: rPhotoOnly, // Exclude thumbnails
			bRecurse: true,
			bFlatten: false,
			bIncludeFolders: false,
			iFilePathType: 3,
			fnSuccess: fnListSuccess,
			fnError: fnListError
		};

		list(oOptions);
	}
	
	/**
	* Moves the contents of a folder. Target folder will be created if it doesn't exist
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sSource - An absolute path to a file/folder
	* @param {string} sTarget - An absolute path to a folder
	* @param {function} fnSuccess - Success callback
	* @param {function} fnError - Error callback (object)
	*/		
	function moveFolder(sSourceFolder,sTargetFolder,fnSuccess,fnError){
		
		var aErrors = [];
		//console.log(aErrors);
		
		var fnListSuccess = function(aoFiles){
			console.log(aoFiles);
			//hideLoading()
			
			FileApi.walk(aoFiles,function(oFile,sPath,fnNext){
				console.log(oFile);
				console.log('sPath: ' + sPath + ': ' + oFile.name);
				
				sPath = stripOuterSlashes(sPath);
				
				var sSource = oFile['nativeURL'];
				var sTarget = sTargetFolder + sPath;
				
				moveFile(sSource,sTarget,null,function(){
					fnNext();
				},function(oError){
					//aErrors.push(oError);
					aErrors.push({sSource: sSource,sTarget: sTarget});
					fnNext();
				});
				
				
			},function(){
				console.log('Recursive file copy complete');
				
				if (aErrors.length > 0){
					if (typeof(fnError) != 'undefined'){
						fnError();
					}							
				} else {
					if (typeof(fnSuccess) != 'undefined'){
						fnSuccess();
					}					
				}
			});
			
			// NEEDS ERROR HANDLING 12/30/16
		};
		
		var fnListError = function(){
		};
		
		
		var oOptions = {
			sPath: sSourceFolder,
			//rFilter: rPhotoOnly, // Exclude thumbnails
			bRecurse: true,
			bFlatten: false,
			bIncludeFolders: false,
			iFilePathType: 3,
			fnSuccess: fnListSuccess,
			fnError: fnListError
		};

		list(oOptions);
	}
	
	/**
	* Walk through nested file object
	* 
	* @public
	* @memberOf FileApi#
	* @param {object} aoFiles - A nested file system object (returned from list)
	* @param {function} fnEach - A function to run against each file
	* @param {function} fnDone - Fires when all files have been processed
	* @param {string} sPath
	* @param {integer} iDepth
	*/	
	function walk(aoFiles,fnEach,fnDone, sPath, iDepth){
		if (!iDepth) iDepth = 0;
		if (!sPath) sPath = '/';
		
		//console.log('iDepth: ' + iDepth + ', sPath: ' + sPath)
		//console.log(aoFiles);
		
		var fnQueueFile = function(iIndex,oFile){
			return function(fnNext){
				// Copy file
				//console.log('copying file: ' + oFile['name']);
				console.log(oFile)
				
				fnEach(oFile,sPath,function(){
					fnNext();
				});
			};
		};
		
		var fnQueueFolder = function(iIndex,oFolder){
			return function(fnNext){
				// Copy file
				//console.log('copying folder: ' + oFolder['name']);
				walk(oFolder,fnEach, function(){
					fnNext();
				},sPath + oFolder['name'] + '/',iDepth+1);
			};
		};
		
		var fnCopyFolders = function(){
			// Copy folders
			if (typeof(aoFiles['folders']) != 'undefined' && aoFiles['folders'].length > 0){

				var aFunctions = [];
				
				for (var x=0;x<aoFiles['folders'].length;x++){
					//console.log(aoFiles['folders'][x]);
					aFunctions.push(fnQueueFolder(x,aoFiles['folders'][x]));
				}
				
				async.series(aFunctions,function(oError,oResult){
					if (oError){
						console.log(oError);
					}
					
					fnDone();
				});
			} else {
				//console.log('Folders empty, firing callback');
				fnDone();
				return;
			}
		}
		
		//console.log(aoFiles['files'])
		
		// Copy files
		if (typeof(aoFiles['files']) != 'undefined' && aoFiles['files'].length > 0){
			var aFunctions = [];
			
			for (var x=0;x<aoFiles['files'].length;x++){
				//console.log(aoFiles['files'][x]);
				aFunctions.push(fnQueueFile(x,aoFiles['files'][x]));
			}
			
			async.series(aFunctions,function(oError,oResult){
				if (oError){
					console.error(oError);
					fnDone();
				} else {
					fnCopyFolders();
				}
			});
		} else {
			console.log('Files empty, trying folders');
			fnCopyFolders();
		}				
	}	
	
	function writeFile(sPath,sFilename,sContents,fnSuccess,fnError){
		// Write dummy file 
		var fnWrite = function(oDirectory){
			if (oDirectory.isDirectory){
				oDirectory.getFile(sFilename,{create: true, exclusive: false},function(fileEntry){
					fileEntry.createWriter(function(writer){
						
						//console.log(writer);
						//console.log("writer.fileName: " + writer.fileName);
						
						//var filePath = "cdvfile://localhost/" + fileSystem.name + "/" + fileEntry.name;
						
						writer.onwrite = function(evt) {
							console.log('FileApi: writeFile createWriter onwrite');
						};
						
						writer.onwriteend = function(evt) {
							console.log('FileApi: writeFile createWriter onwriteend');
							//console.log('fileEntry',fileEntry);
							//console.log("writer.fileName: " + writer.fileName);
							
							if (typeof(fnSuccess) == 'function'){
								fnSuccess(fileEntry);
							}
							
							
							// See if this function works at aall
			/*				resolve(filePath, 
							function(fileEntry2){
								console.log("resolved");
							},
							function(error){
								console.log("unable to resolve");
							});*/
						};
						
						writer.write(sContents);
						//writer.abort();
					}, function(oError){
						console.error('FileApi: writeFile createWriter error',oError);
						
						if (typeof(fnError) == 'function'){
							fnError();
						}
					});
				});
			}
		}

		// ATTEMPT to resolve the target
		resolve(sPath,function(oTarget){	
			console.log('FileApi: writeFile - Target exists',oTarget);
			//
			fnWrite(oTarget);
		},function(){
			// If the path can't be resolved, attempt to create it
			createDirectoryAny(sPath,
			function(oTarget){
				//console.log('FileApi: moveFile - Target created',oTarget);
				fnWrite(oTarget);
			}, 
			fnError);
		});
	}

	/**
	* Get file info
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sSource - The file path
	* @param {function} fnSuccess - Success callback (object)
	* @param {function} fnError - Error callback (string)
	*/
	function fileInfo(sSource,fnSuccess,fnError){
	
		var fnFileSuccess = function(oFile){
			/*	
			Properties
		
			name: The name of the file. (DOMString)
			fullPath: The full path of the file including the file name. (DOMString)
			type: The mime type of the file. (DOMString)
			lastModifiedDate: The last time the file was modified. (Date)
		
			size: The size of the file in bytes. (long)
			*/
			
			fnSuccess(oFile);
		}
		
		var fnFileError = function(oError){
			//console.log(oError);
			fnError('Error running file method on file entry');
		}
	
		console.log('FileApi: fileInfo');
		resolve(sSource,function(oEntry){
			//console.log(oEntry);
			
			if (oEntry['isFile']){
				oEntry.file(fnFileSuccess,fnFileError);
			} else {
				fnError(sSource + ' is not a file');
			}
	
		},fnError);
	
	}

	/**
	* Determine if file or folder exists
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sSource - The file/folder path
	* @param {function} fnComplete - Success callback (boolean)
	*/	
	function exists(sSource,fnComplete){
		console.log('FileApi: exists');
		resolve(sSource,
		function(oEntry){
			fnComplete(true);
		},
		function(){
			fnComplete(false);
		});		
	}
	
	/**
	* Wrapper for window.resolveLocalFileSystemURL
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sSource - The file/folder path
	* @param {function} fnSuccess - Success callback (object)
	* @param {function} fnError - Error callback (object)
	*/	
	function resolve(sSource,fnSuccess,fnError){
		//console.log('FileApi: exists');
		window.resolveLocalFileSystemURL(sSource,fnSuccess,fnError);		
	}
	
	/**
	* Upload a single file
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sPath - The file path
	* @param {string} sTargetUrl - The target url
	* @param {object} oOptions - The target url
	* @param {function} fnSuccess - Success callback (object)
	* @param {function} fnError - Error callback (object)
	* @param {function} fnProgress - Progress (object)
	*/	
	function uploadFile(sPath, sTargetUrl, oOptions, fnSuccess, fnError,fnProgress){
	
		var oDefaults = new FileUploadOptions(); // Prepare file upload settings
	
		//oDefaults['fileName'] = oPhoto['filename'];
		//oDefaults['mimeType'] = 'image/jpeg';
		//oDefaults['params'] = {json:JSON.stringify(oPhoto)};
		//oDefaults['chunkedMode'] = false;
		
		oUploadOptions = $.extend( {}, oDefaults, oOptions);
	
		var ft = new FileTransfer();
		ft.onprogress = fnProgress; // Upload progress callback
		ft.upload(sPath,sTargetUrl,fnSuccess,fnError,oUploadOptions);
	}

	/**
	* Download a single file
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sSource - The file path
	* @param {string} sTargetUrl - The target url
	* @param {function} fnSuccess - Success callback (object)
	* @param {function} fnError - Error callback (object)
	* @param {function} fnProgress - Progress (object)
	* @param {object} oOptions - The target url
	*/
	function downloadFile(sSourceUrl,sTarget,fnSuccess,fnError,fnProgress,oOptions){
		if (oConstants['bIsiOS']){
			sTarget = encodeURI(sTarget); // Fix for spaces not working on iOS	
		}
		
		console.log(sTarget);
		
		var ft = new FileTransfer();
		ft.onprogress = fnProgress; // Upload progress callback
		ft.download(sSourceUrl,sTarget,fnSuccess,fnError,oOptions);		
	}

	/**
	* Open a file natively
	* 
	* @public
	* @memberOf FileApi#
	* @param {string} sPath - The file path
	* @param {string} sType - The mime type
	* @param {boolean} bForcePlugin - Force the file to open in the fileOpener plugin
	*/
	function open(sPath,sType, bForcePlugin){
		//sPath = encodeURI(sPath); // Fix for spaces in filename

		if(oConstants['bIsiOS'] && !bForcePlugin){
			cordova.InAppBrowser.open(sPath,'_blank','enableViewportScale=yes');
			//cordova.InAppBrowser.open(sPath,'_blank');
			
		} else {
			cordova.plugins.fileOpener2.open(
				sPath, 
				sType, 
				{
					success : function(){
						console.log('File opened successfully: ' + sPath);
					},
					error : function(){
						msg_error('Unable to open file');
						console.log('File failed to open: ' + sPath);
					}
				} 
			);				
		}
	}
	
	return {
		'createDirectory': createDirectory,
		'createDirectoryAny': createDirectoryAny,
		'removeDirectory': removeDirectory,
		'getPersistentPath': getPersistentPath,
		'getError': getError,
		'list': list,
		'deleteFile': deleteFile,
		'copy': copy,
		'copyFile': copyFile,
		'moveFile': moveFile,
		'copyFolder': copyFolder,
		'moveFolder': moveFolder,
		'walk': walk,
		'walkFS': walkFS,
		'fileInfo': fileInfo,
		'exists': exists,
		'uploadFile': uploadFile,
		'downloadFile': downloadFile,
		'resolve': resolve,
		'open': open,
		writeFile: writeFile
	};
})();
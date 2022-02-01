// Copyright 1998-2018 Epic Games, Inc. All Rights Reserved.

using System.IO;
using UnrealBuildTool;

public class RDCmdlets : ModuleRules
{
	public RDCmdlets(ReadOnlyTargetRules Target) : base(Target)
	{
#if UE_4_22_OR_LATER
		PCHUsage = PCHUsageMode.NoPCHs;
#else
		PCHUsage = PCHUsageMode.NoSharedPCHs;
#endif
		
		bUseRTTI = true;

		PublicDependencyModuleNames.Add("Core");
		PublicDependencyModuleNames.Add("Engine");
		PublicDependencyModuleNames.Add("CoreUObject");
		PublicDependencyModuleNames.Add("SlateNullRenderer");
		PublicDependencyModuleNames.Add("RHI");
		PublicDependencyModuleNames.Add("RenderCore");
		PublicDependencyModuleNames.Add("Slate");
	}
}

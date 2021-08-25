using System.Linq;
using JetBrains.Metadata.Reader.API;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Navigation;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.Cpp.Caches;
using JetBrains.ReSharper.Psi.Cpp.Language;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.Util;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Util.Caches;
using RiderPlugin.UnrealLink.Model;
using RiderPlugin.UnrealLink.Model.FrontendBackend;

namespace RiderPlugin.UnrealLink
{
    [SolutionComponent]
    public class EditorNavigator
    {
        private readonly CppGlobalSymbolCache _cppSymbolNameCache;
        private readonly IPsiServices _psiServices;
        private const int MaxSizeOfCache = 1 << 6;
        private readonly DirectMappedCache<string, CppClassSymbol> _classCache = new DirectMappedCache<string, CppClassSymbol>(MaxSizeOfCache);
        private readonly DirectMappedCache<string, CppDeclaratorSymbol> _methodCache = new DirectMappedCache<string, CppDeclaratorSymbol>(MaxSizeOfCache);


        public EditorNavigator(CppGlobalSymbolCache cppSymbolNameCache, IPsiServices psiServices)
        {
            _cppSymbolNameCache = cppSymbolNameCache;
            _psiServices = psiServices;
        }

        private CppClassSymbol GetClassSymbol(string name) =>
            _classCache.GetOrCreate(name, s =>
            {
                var symbolsByShortName = _cppSymbolNameCache.SymbolNameCache.GetSymbolsByShortName(s);
                var classSymbol = symbolsByShortName.OfType<CppClassSymbol>().SingleOrNull();
                return classSymbol;
            });

        private CppDeclaratorSymbol GetMethodSymbol(CppClassSymbol classSymbol, string method) =>
            _methodCache.GetOrCreate(method, s =>
            {
                return classSymbol.Children.OfType<CppDeclaratorSymbol>()
                    .Where(symbol => s == symbol.Name.Name.ToString()).SingleOrNull();
            });

        public bool NavigateToClass(UClassName uClass)
        {
            var classSymbol = GetClassSymbol(uClass.Name.Data);
            if (classSymbol == null) return false;
            var declaredElement = new CppParserSymbolDeclaredElement(_psiServices, classSymbol);

            using (ReadLockCookie.Create())
            {
                using (CompilationContextCookie.GetOrCreate(UniversalModuleReferenceContext.Instance))
                {
                    declaredElement.Navigate(true);
                }
            }

            return true;
        }

        public bool NavigateToMethod(MethodReference methodReference)
        {
            var declaredElement = MethodDeclaredElement(methodReference);
            if (declaredElement == null) return false;
            using (ReadLockCookie.Create())
            {
                using (CompilationContextCookie.GetOrCreate(UniversalModuleReferenceContext.Instance))
                {
                    declaredElement.Navigate(true);
                }
            }

            return true;
        }

        private CppParserSymbolDeclaredElement MethodDeclaredElement(MethodReference methodReference)
        {
            var (uClass, method) = methodReference;
            var classSymbol = GetClassSymbol(uClass.Name.Data);
            if (classSymbol == null)
                return null;
            var methodSymbol = GetMethodSymbol(classSymbol, method.Data);
            return new CppParserSymbolDeclaredElement(_psiServices, methodSymbol);
        }

        public bool IsMethodReference(MethodReference methodReference)
        {
            return MethodDeclaredElement(methodReference) != null;
        }
    }
}